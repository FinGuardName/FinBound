import hashlib
import io
import json
import sys
from dataclasses import replace
from pathlib import Path
from types import SimpleNamespace

import pytest

from app.prompt.model import (
    DomainPromptClassifier,
    HybridPromptClassifier,
    OnnxPromptClassifier,
    PromptDetectorConfig,
    PromptModelError,
)


class FakeComponent:
    def __init__(self, score: float, failure: bool = False) -> None:
        self.score = score
        self.failure = failure

    def check_ready(self) -> None:
        if self.failure:
            raise PromptModelError("unavailable")

    def predict_attack_score(self, text: str) -> float:
        if self.failure:
            raise PromptModelError("unavailable")
        return self.score


def test_hybrid_uses_independently_calibrated_max_evidence() -> None:
    config = PromptDetectorConfig.load()
    classifier = HybridPromptClassifier(
        config,
        pretrained=FakeComponent(config.pretrained_score_threshold * 0.2),
        domain_adapter=FakeComponent(config.domain_adapter_score_threshold),
    )

    assert classifier.predict_attack_score("input is not retained") == 1.0


def test_hybrid_readiness_fails_when_any_required_component_fails() -> None:
    config = PromptDetectorConfig.load()
    classifier = HybridPromptClassifier(
        config,
        pretrained=FakeComponent(0.0),
        domain_adapter=FakeComponent(0.0, failure=True),
    )

    with pytest.raises(PromptModelError):
        classifier.check_ready()


def test_onnx_session_loads_the_exact_bytes_that_were_verified(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    model_bytes = b"verified-onnx-bytes"
    tokenizer_bytes = b'{"verified":true}'
    model_path = tmp_path / "onnx/int8/model.onnx"
    tokenizer_path = tmp_path / "onnx/int8/tokenizer.json"
    model_path.parent.mkdir(parents=True)
    model_path.write_bytes(model_bytes)
    tokenizer_path.write_bytes(tokenizer_bytes)
    config = replace(
        PromptDetectorConfig.load(),
        model_artifact_path="onnx/int8/model.onnx",
        model_artifact_sha256=hashlib.sha256(model_bytes).hexdigest(),
        tokenizer_artifacts=(
            ("onnx/int8/tokenizer.json", hashlib.sha256(tokenizer_bytes).hexdigest()),
        ),
    )
    loaded: dict[str, object] = {}

    def create_session(artifact: bytes, providers: list[str]) -> object:
        loaded["artifact"] = artifact
        loaded["providers"] = providers
        return object()

    fake_tokenizer = SimpleNamespace(from_pretrained=lambda *_args, **_kwargs: object())
    monkeypatch.setitem(
        sys.modules, "onnxruntime", SimpleNamespace(InferenceSession=create_session)
    )
    monkeypatch.setitem(sys.modules, "transformers", SimpleNamespace(AutoTokenizer=fake_tokenizer))

    OnnxPromptClassifier(config, model_dir=tmp_path).check_ready()

    assert loaded == {
        "artifact": model_bytes,
        "providers": ["CPUExecutionProvider"],
    }


def test_onnx_readiness_rejects_modified_tokenizer_artifact(tmp_path: Path) -> None:
    model_bytes = b"verified-onnx-bytes"
    model_path = tmp_path / "onnx/int8/model.onnx"
    tokenizer_path = tmp_path / "onnx/int8/tokenizer.json"
    model_path.parent.mkdir(parents=True)
    model_path.write_bytes(model_bytes)
    tokenizer_path.write_bytes(b"modified-tokenizer")
    config = replace(
        PromptDetectorConfig.load(),
        model_artifact_path="onnx/int8/model.onnx",
        model_artifact_sha256=hashlib.sha256(model_bytes).hexdigest(),
        tokenizer_artifacts=(("onnx/int8/tokenizer.json", "0" * 64),),
    )

    with pytest.raises(PromptModelError, match="tokenizer artifact digest"):
        OnnxPromptClassifier(config, model_dir=tmp_path).check_ready()


def test_onnx_readiness_rejects_wrong_model_digest(tmp_path: Path) -> None:
    model_path = tmp_path / "onnx/int8/model.onnx"
    model_path.parent.mkdir(parents=True)
    model_path.write_bytes(b"modified-model")
    config = replace(
        PromptDetectorConfig.load(),
        model_artifact_path="onnx/int8/model.onnx",
        model_artifact_sha256="0" * 64,
        tokenizer_artifacts=(("onnx/int8/tokenizer.json", "0" * 64),),
    )

    with pytest.raises(PromptModelError, match="model artifact digest"):
        OnnxPromptClassifier(config, model_dir=tmp_path).check_ready()


def test_domain_adapter_loads_the_exact_bytes_that_were_verified(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    artifact_bytes = b"verified-joblib-bytes"
    artifact = tmp_path / "adapter.joblib"
    artifact.write_bytes(artifact_bytes)
    config = replace(
        PromptDetectorConfig.load(),
        domain_adapter_artifact_sha256=hashlib.sha256(artifact_bytes).hexdigest(),
    )
    classifier = object()

    def load_bundle(source: io.BytesIO) -> dict[str, object]:
        assert isinstance(source, io.BytesIO)
        assert source.read() == artifact_bytes
        return {
            "datasetSha256": config.approved_dataset_sha256,
            "classifier": classifier,
        }

    monkeypatch.setenv("FINGUARD_PROMPT_DOMAIN_ADAPTER_PATH", str(artifact))
    monkeypatch.setattr("joblib.load", load_bundle)
    domain_classifier = DomainPromptClassifier(config)

    domain_classifier.check_ready()

    assert domain_classifier._classifier is classifier


def test_domain_adapter_readiness_rejects_wrong_digest(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    artifact = tmp_path / "adapter.joblib"
    artifact.write_bytes(b"modified-adapter")
    monkeypatch.setenv("FINGUARD_PROMPT_DOMAIN_ADAPTER_PATH", str(artifact))
    config = replace(
        PromptDetectorConfig.load(),
        domain_adapter_artifact_sha256="0" * 64,
    )

    with pytest.raises(PromptModelError, match="domain adapter artifact digest"):
        DomainPromptClassifier(config).check_ready()


def test_config_rejects_tokenizer_path_outside_model_bundle(tmp_path: Path) -> None:
    source = Path(__file__).parents[1] / "models" / "prompt_detector.json"
    payload = json.loads(source.read_text(encoding="utf-8"))
    payload["tokenizerArtifactSha256"] = {"../tokenizer.json": "0" * 64}
    config_path = tmp_path / "prompt_detector.json"
    config_path.write_text(json.dumps(payload), encoding="utf-8")

    with pytest.raises(PromptModelError, match="artifact path"):
        PromptDetectorConfig.load(config_path)
