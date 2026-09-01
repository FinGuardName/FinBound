import hashlib
from datetime import UTC, datetime
from pathlib import Path

from app.prompt.model import (
    HybridPromptClassifier,
    PromptClassifier,
    PromptDetectorConfig,
    PromptModelError,
)
from app.prompt.rules import detect_rule_matches, normalize_prompt_text
from app.schemas.prompt import PromptAttackType, PromptRiskRequest, PromptRiskResponse


class PromptInputError(ValueError):
    pass


def input_hash(input_text: str) -> str:
    digest = hashlib.sha256(input_text.encode("utf-8")).hexdigest()
    return f"sha256:{digest}"


class PromptRiskService:
    def __init__(
        self,
        classifier: PromptClassifier | None = None,
        config_path: Path | None = None,
    ) -> None:
        self._config = PromptDetectorConfig.load(config_path)
        self._classifier = classifier or HybridPromptClassifier(self._config)

    def check_ready(self) -> None:
        self._classifier.check_ready()

    def _calibrate_model_risk(self, model_score: float) -> float:
        model_threshold = self._config.hybrid_evidence_threshold
        block_threshold = self._config.prompt_block_threshold
        if model_score < model_threshold:
            return (block_threshold - 0.01) * (model_score / model_threshold)
        if model_threshold == 1:
            return 1.0
        tail_position = (model_score - model_threshold) / (1 - model_threshold)
        return block_threshold + ((1 - block_threshold) * tail_position)

    def evaluate(self, request: PromptRiskRequest) -> PromptRiskResponse:
        if input_hash(request.input_text) != request.input_hash:
            raise PromptInputError("PROMPT_INPUT_HASH_MISMATCH")
        normalized = normalize_prompt_text(request.input_text)
        if not normalized:
            raise PromptInputError("PROMPT_INPUT_EMPTY")

        matches = detect_rule_matches(normalized)
        model_score = self._classifier.predict_attack_score(normalized)
        rule_score = self._config.rule_risk if matches else 0.0
        prompt_risk = max(rule_score, self._calibrate_model_risk(model_score))
        prompt_risk = round(min(1.0, max(0.0, prompt_risk)), 6)
        detected = prompt_risk >= self._config.prompt_block_threshold
        attack_type = (
            matches[0].attack_type
            if detected and matches
            else (PromptAttackType.UNKNOWN_PROMPT_ATTACK if detected else None)
        )

        return PromptRiskResponse(
            detected=detected,
            prompt_risk=prompt_risk,
            attack_type=attack_type,
            matched_rules=[match.rule_id for match in matches],
            input_hash=request.input_hash,
            model_version=self._config.model_version,
            evaluated_at=datetime.now(UTC),
        )


__all__ = [
    "PromptInputError",
    "PromptModelError",
    "PromptRiskService",
    "input_hash",
]
