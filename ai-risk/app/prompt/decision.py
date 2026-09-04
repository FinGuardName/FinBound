from dataclasses import dataclass

from app.prompt.model import PromptDetectorConfig
from app.prompt.rules import RuleMatch
from app.schemas.prompt import PromptRiskLevel


@dataclass(frozen=True)
class PromptRiskDecision:
    risk_level: PromptRiskLevel
    prompt_risk: float

    @property
    def detected(self) -> bool:
        return self.risk_level is PromptRiskLevel.CRITICAL


def decide_prompt_risk(
    model_score: float,
    matches: tuple[RuleMatch, ...],
    config: PromptDetectorConfig,
) -> PromptRiskDecision:
    """Turn model evidence and explainable rules into a policy-neutral risk level.

    Rules can raise a request to ALERT or corroborate medium model evidence, but lexical
    rules alone never produce CRITICAL. This keeps semantic model evidence primary while
    preserving rules as auditable supporting evidence.
    """
    has_rule_evidence = bool(matches)
    has_supporting_model_evidence = model_score >= config.model_support_threshold
    has_high_model_evidence = model_score >= config.model_high_threshold

    model_risk = _calibrate_model_risk(model_score, config)
    rule_risk = config.rule_alert_risk if has_rule_evidence else 0.0
    prompt_risk = max(model_risk, rule_risk)

    if has_high_model_evidence or (has_supporting_model_evidence and has_rule_evidence):
        risk_level = PromptRiskLevel.CRITICAL
        prompt_risk = max(prompt_risk, config.prompt_block_threshold)
    elif has_supporting_model_evidence or has_rule_evidence:
        risk_level = PromptRiskLevel.ALERT
        prompt_risk = max(prompt_risk, config.prompt_alert_threshold)
        prompt_risk = min(prompt_risk, config.prompt_block_threshold - 0.01)
    else:
        risk_level = PromptRiskLevel.LOW
        prompt_risk = min(prompt_risk, config.prompt_alert_threshold - 0.01)

    return PromptRiskDecision(
        risk_level=risk_level,
        prompt_risk=round(min(1.0, max(0.0, prompt_risk)), 6),
    )


def _calibrate_model_risk(model_score: float, config: PromptDetectorConfig) -> float:
    support = config.model_support_threshold
    high = config.model_high_threshold
    alert = config.prompt_alert_threshold
    block = config.prompt_block_threshold

    if model_score < support:
        return (alert - 0.01) * (model_score / support)
    if model_score < high:
        position = (model_score - support) / (high - support)
        return alert + ((block - alert - 0.01) * position)
    if high == 1:
        return 1.0
    tail_position = (model_score - high) / (1 - high)
    return block + ((1 - block) * tail_position)
