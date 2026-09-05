import hashlib
import io
import json
import os
from dataclasses import dataclass
from pathlib import Path, PurePosixPath
from typing import Protocol

import numpy as np

DEFAULT_CONFIG_PATH = Path(__file__).resolve().parents[2] / "models" / "prompt_detector.json"
DEFAULT_MODEL_DIR = Path(__file__).resolve().parents[2] / "models" / "prompt_guard_onnx"
CONFIG_PATH_ENV = "FINGUARD_PROMPT_CONFIG_PATH"
MODEL_DIR_ENV = "FINGUARD_PROMPT_MODEL_DIR"
DOMAIN_ADAPTER_PATH_ENV = "FINGUARD_PROMPT_DOMAIN_ADAPTER_PATH"


class PromptModelError(RuntimeError):
    pass


class PromptClassifier(Protocol):
    def check_ready(self) -> None: ...

    def predict_attack_score(self, text: str) -> float: ...


@dataclass(frozen=True)
class PromptDetectorConfig:
    model_version: str
    model_id: str
    model_revision: str
    model_artifact_path: str
    model_artifact_sha256: str
    tokenizer_path: str
    tokenizer_artifacts: tuple[tuple[str, str], ...]
    injection_label_id: int
    max_tokens: int
    rule_alert_risk: float
    pretrained_score_threshold: float
    domain_adapter_artifact_path: str
    domain_adapter_artifact_sha256: str
    domain_adapter_score_threshold: float
    model_support_threshold: float
    model_high_threshold: float
    prompt_alert_threshold: float
    prompt_block_threshold: float
    dataset_version: str
    approved_dataset_sha256: str

    @classmethod
    def load(cls, path: Path | None = None) -> "PromptDetectorConfig":
        configured = os.getenv(CONFIG_PATH_ENV)
        selected_path = path or (Path(configured) if configured else DEFAULT_CONFIG_PATH)
        try:
            payload = json.loads(selected_path.read_text(encoding="utf-8"))
            config = cls(
                model_version=payload["modelVersion"],
                model_id=payload["modelId"],
                model_revision=payload["modelRevision"],
                model_artifact_path=payload["modelArtifactPath"],
                model_artifact_sha256=payload["modelArtifactSha256"],
                tokenizer_path=payload["tokenizerPath"],
                tokenizer_artifacts=tuple(sorted(payload["tokenizerArtifactSha256"].items())),
                injection_label_id=payload["injectionLabelId"],
                max_tokens=payload["maxTokens"],
                rule_alert_risk=payload["ruleAlertRisk"],
                pretrained_score_threshold=payload["pretrainedScoreThreshold"],
                domain_adapter_artifact_path=payload["domainAdapterArtifactPath"],
                domain_adapter_artifact_sha256=payload["domainAdapterArtifactSha256"],
                domain_adapter_score_threshold=payload["domainAdapterScoreThreshold"],
                model_support_threshold=payload["modelSupportThreshold"],
                model_high_threshold=payload["modelHighThreshold"],
                prompt_alert_threshold=payload["promptAlertThreshold"],
                prompt_block_threshold=payload["promptBlockThreshold"],
                dataset_version=payload["datasetVersion"],
                approved_dataset_sha256=payload["approvedDatasetSha256"],
            )
        except (
            AttributeError,
            OSError,
            KeyError,
            TypeError,
            ValueError,
            json.JSONDecodeError,
        ) as error:
            raise PromptModelError("Prompt detector configuration is invalid") from error
        if not config.model_version or not config.model_id or not config.model_revision:
            raise PromptModelError("Prompt detector identity is invalid")
        if config.injection_label_id < 0:
            raise PromptModelError("Prompt detector injection label is invalid")
        if not 1 <= config.max_tokens <= 512:
            raise PromptModelError("Prompt detector maxTokens is invalid")
        if not 0 <= config.rule_alert_risk <= 1:
            raise PromptModelError("Prompt detector ruleAlertRisk is invalid")
        if not 0 < config.pretrained_score_threshold <= 1:
            raise PromptModelError("Prompt detector pretrainedScoreThreshold is invalid")
        _validate_relative_artifact_path(config.domain_adapter_artifact_path)
        if not _is_sha256(config.domain_adapter_artifact_sha256):
            raise PromptModelError("Prompt detector domain adapter digest is invalid")
        if not _is_sha256(config.approved_dataset_sha256):
            raise PromptModelError("Prompt detector dataset digest is invalid")
        if not 0 < config.domain_adapter_score_threshold <= 1:
            raise PromptModelError("Prompt detector domainAdapterScoreThreshold is invalid")
        if not 0 < config.model_support_threshold < config.model_high_threshold <= 1:
            raise PromptModelError("Prompt detector model evidence thresholds are invalid")
        if not 0 < config.prompt_alert_threshold < config.prompt_block_threshold <= 1:
            raise PromptModelError("Prompt detector risk thresholds are invalid")
        if (
            not config.prompt_alert_threshold
            <= config.rule_alert_risk
            < config.prompt_block_threshold
        ):
            raise PromptModelError("Prompt detector ruleAlertRisk must remain in ALERT range")
        if not _is_sha256(config.model_artifact_sha256):
            raise PromptModelError("Prompt detector artifact digest is invalid")
        model_path = _validate_relative_artifact_path(config.model_artifact_path)
        tokenizer_root = _validate_relative_artifact_path(config.tokenizer_path)
        if not model_path.is_relative_to(tokenizer_root) or model_path == tokenizer_root:
            raise PromptModelError("Prompt model artifact path is invalid")
        if not config.tokenizer_artifacts:
            raise PromptModelError("Prompt tokenizer artifact manifest is empty")
        for artifact_path, artifact_digest in config.tokenizer_artifacts:
            candidate = _validate_relative_artifact_path(artifact_path)
            if not candidate.is_relative_to(tokenizer_root) or candidate == tokenizer_root:
                raise PromptModelError("Prompt tokenizer artifact path is invalid")
            if not _is_sha256(artifact_digest):
                raise PromptModelError("Prompt tokenizer artifact digest is invalid")
        return config


def _is_sha256(value: object) -> bool:
    return (
        isinstance(value, str)
        and len(value) == 64
        and all(character in "0123456789abcdef" for character in value)
    )


def _validate_relative_artifact_path(value: object) -> PurePosixPath:
    if not isinstance(value, str) or not value or "\\" in value:
        raise PromptModelError("Prompt artifact path is invalid")
    path = PurePosixPath(value)
    if path.is_absolute() or path == PurePosixPath(".") or ".." in path.parts:
        raise PromptModelError("Prompt artifact path is invalid")
    return path


def read_verified_artifact(path: Path, expected_sha256: str, label: str) -> bytes:
    try:
        artifact_bytes = path.read_bytes()
    except OSError as error:
        raise PromptModelError(f"{label} is unavailable") from error
    if hashlib.sha256(artifact_bytes).hexdigest() != expected_sha256:
        raise PromptModelError(f"{label} digest does not match")
    return artifact_bytes


class OnnxPromptClassifier:
    def __init__(
        self,
        config: PromptDetectorConfig,
        model_dir: Path | None = None,
    ) -> None:
        configured = os.getenv(MODEL_DIR_ENV)
        self._model_dir = model_dir or (Path(configured) if configured else DEFAULT_MODEL_DIR)
        self._config = config
        self._tokenizer = None
        self._session = None

    def _load(self) -> None:
        if self._session is not None and self._tokenizer is not None:
            return
        model_path = self._model_dir / self._config.model_artifact_path
        model_bytes = read_verified_artifact(
            model_path,
            self._config.model_artifact_sha256,
            "Prompt model artifact",
        )
        for artifact_path, artifact_sha256 in self._config.tokenizer_artifacts:
            read_verified_artifact(
                self._model_dir / artifact_path,
                artifact_sha256,
                "Prompt tokenizer artifact",
            )
        try:
            import onnxruntime as ort
            from transformers import AutoTokenizer

            tokenizer = AutoTokenizer.from_pretrained(
                self._model_dir / self._config.tokenizer_path,
                local_files_only=True,
                fix_mistral_regex=True,
            )
            session = ort.InferenceSession(
                model_bytes,
                providers=["CPUExecutionProvider"],
            )
        except Exception as error:
            raise PromptModelError("Prompt model or tokenizer could not be loaded") from error
        self._tokenizer = tokenizer
        self._session = session

    def check_ready(self) -> None:
        self._load()

    def predict_attack_score(self, text: str) -> float:
        self._load()
        try:
            encoded = self._tokenizer(
                text,
                return_tensors="np",
                truncation=True,
                max_length=self._config.max_tokens,
            )
            input_names = {item.name for item in self._session.get_inputs()}
            feed = {name: value for name, value in encoded.items() if name in input_names}
            logits = np.asarray(self._session.run(None, feed)[0], dtype=float)[0]
            shifted = logits - np.max(logits)
            probabilities = np.exp(shifted) / np.exp(shifted).sum()
            score = float(probabilities[self._config.injection_label_id])
        except Exception as error:
            raise PromptModelError("Prompt model inference failed") from error
        if not np.isfinite(score) or not 0 <= score <= 1:
            raise PromptModelError("Prompt model returned an invalid score")
        return score


class DomainPromptClassifier:
    def __init__(self, config: PromptDetectorConfig) -> None:
        self._config = config
        self._classifier = None

    def _load(self) -> None:
        if self._classifier is not None:
            return
        configured = os.getenv(DOMAIN_ADAPTER_PATH_ENV)
        artifact = (
            Path(configured)
            if configured
            else DEFAULT_CONFIG_PATH.parent / self._config.domain_adapter_artifact_path
        )
        artifact_bytes = read_verified_artifact(
            artifact,
            self._config.domain_adapter_artifact_sha256,
            "Prompt domain adapter artifact",
        )
        try:
            import joblib

            bundle = joblib.load(io.BytesIO(artifact_bytes))
            if bundle["datasetSha256"] != self._config.approved_dataset_sha256:
                raise PromptModelError("Prompt domain adapter dataset digest does not match")
            classifier = bundle["classifier"]
        except PromptModelError:
            raise
        except Exception as error:
            raise PromptModelError("Prompt domain adapter could not be loaded") from error
        self._classifier = classifier

    def check_ready(self) -> None:
        self._load()

    def predict_attack_score(self, text: str) -> float:
        self._load()
        try:
            score = float(self._classifier.predict_proba([text])[0][1])
        except Exception as error:
            raise PromptModelError("Prompt domain adapter inference failed") from error
        if not np.isfinite(score) or not 0 <= score <= 1:
            raise PromptModelError("Prompt domain adapter returned an invalid score")
        return score


class HybridPromptClassifier:
    """Fuse independently calibrated evidence without turning it into authorization."""

    def __init__(
        self,
        config: PromptDetectorConfig,
        pretrained: PromptClassifier | None = None,
        domain_adapter: PromptClassifier | None = None,
    ) -> None:
        self._config = config
        self._pretrained = pretrained or OnnxPromptClassifier(config)
        self._domain_adapter = domain_adapter or DomainPromptClassifier(config)

    def check_ready(self) -> None:
        self._pretrained.check_ready()
        self._domain_adapter.check_ready()

    def predict_attack_score(self, text: str) -> float:
        pretrained_score = self._pretrained.predict_attack_score(text)
        domain_score = self._domain_adapter.predict_attack_score(text)
        evidence = max(
            pretrained_score / self._config.pretrained_score_threshold,
            domain_score / self._config.domain_adapter_score_threshold,
        )
        return min(1.0, max(0.0, evidence))
