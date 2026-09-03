from datetime import datetime
from enum import StrEnum

from pydantic import Field

from app.schemas.behavior import ContractModel


class InputLanguage(StrEnum):
    KO = "ko"
    EN = "en"
    MIXED = "mixed"


class PromptAttackType(StrEnum):
    IGNORE_PREVIOUS_INSTRUCTION = "IGNORE_PREVIOUS_INSTRUCTION"
    POLICY_BYPASS = "POLICY_BYPASS"
    SYSTEM_PROMPT_EXTRACTION = "SYSTEM_PROMPT_EXTRACTION"
    CROSS_CUSTOMER_ACCESS = "CROSS_CUSTOMER_ACCESS"
    UNAUTHORIZED_TOOL_REQUEST = "UNAUTHORIZED_TOOL_REQUEST"
    UNKNOWN_PROMPT_ATTACK = "UNKNOWN_PROMPT_ATTACK"


class PromptRiskLevel(StrEnum):
    LOW = "LOW"
    ALERT = "ALERT"
    CRITICAL = "CRITICAL"


class PromptRiskRequest(ContractModel):
    agent_run_id: str = Field(min_length=1, max_length=128)
    input_ref: str = Field(min_length=1, max_length=128)
    input_text: str = Field(min_length=1, max_length=4096)
    input_hash: str = Field(pattern=r"^sha256:[0-9a-f]{64}$")
    # Core may not have classified the language yet. The detector is language-agnostic,
    # so preserve that distinction instead of inventing a `mixed` classification.
    content_language: InputLanguage | None = None


class PromptRiskResponse(ContractModel):
    detected: bool
    prompt_risk: float = Field(ge=0.0, le=1.0)
    risk_level: PromptRiskLevel
    attack_type: PromptAttackType | None
    matched_rules: list[str]
    input_hash: str
    model_version: str
    evaluated_at: datetime
