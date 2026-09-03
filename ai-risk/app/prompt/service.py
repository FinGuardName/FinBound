import hashlib
import math
from datetime import UTC, datetime
from pathlib import Path

from app.prompt.decision import decide_prompt_risk
from app.prompt.model import (
    HybridPromptClassifier,
    PromptClassifier,
    PromptDetectorConfig,
    PromptModelError,
)
from app.prompt.rules import detect_rule_matches, normalize_prompt_text
from app.schemas.prompt import (
    PromptAttackType,
    PromptRiskLevel,
    PromptRiskRequest,
    PromptRiskResponse,
)


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

    def evaluate(self, request: PromptRiskRequest) -> PromptRiskResponse:
        if input_hash(request.input_text) != request.input_hash:
            raise PromptInputError("PROMPT_INPUT_HASH_MISMATCH")
        normalized = normalize_prompt_text(request.input_text)
        if not normalized:
            raise PromptInputError("PROMPT_INPUT_EMPTY")

        matches = detect_rule_matches(normalized)
        model_score = self._classifier.predict_attack_score(normalized)
        if not math.isfinite(model_score) or not 0.0 <= model_score <= 1.0:
            raise PromptModelError(
                "Prompt classifier score must be finite and between zero and one"
            )
        decision = decide_prompt_risk(model_score, matches, self._config)
        attack_type = (
            matches[0].attack_type
            if decision.risk_level is not PromptRiskLevel.LOW and matches
            else (
                PromptAttackType.UNKNOWN_PROMPT_ATTACK
                if decision.risk_level is not PromptRiskLevel.LOW
                else None
            )
        )

        return PromptRiskResponse(
            detected=decision.detected,
            prompt_risk=decision.prompt_risk,
            risk_level=decision.risk_level,
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
