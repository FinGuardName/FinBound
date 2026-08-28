from datetime import datetime
from enum import StrEnum

from pydantic import BaseModel, ConfigDict, Field, field_validator
from pydantic.alias_generators import to_camel


class ContractModel(BaseModel):
    model_config = ConfigDict(
        alias_generator=to_camel,
        populate_by_name=True,
        extra="forbid",
    )


class Decision(StrEnum):
    ALLOW = "ALLOW"
    BLOCK = "BLOCK"


class HistoryStatus(StrEnum):
    READY = "READY"
    COLD_START = "COLD_START"


class BehaviorRiskLevel(StrEnum):
    LOW = "LOW"
    ALERT = "ALERT"
    CRITICAL = "CRITICAL"


class FinancialTool(StrEnum):
    CREDIT_SCORE_READ = "CREDIT_SCORE_READ"
    INCOME_READ = "INCOME_READ"
    DEBT_READ = "DEBT_READ"


class FinancialDataType(StrEnum):
    CREDIT_SCORE = "CREDIT_SCORE"
    INCOME = "INCOME"
    DEBT = "DEBT"


class TimezoneAwareModel(ContractModel):
    @field_validator("requested_at", check_fields=False)
    @classmethod
    def require_timezone(cls, value: datetime) -> datetime:
        if value.tzinfo is None or value.utcoffset() is None:
            raise ValueError("requestedAt must include a timezone")
        return value


class CompletedBehaviorEvent(TimezoneAwareModel):
    request_id: str
    case_id: str
    target_consumer_id: str
    tool: FinancialTool
    requested_at: datetime
    decision: Decision
    success: bool
    latency_ms: int = Field(ge=0)
    requested_data: list[FinancialDataType] = Field(default_factory=list)


class CurrentToolCallAttempt(TimezoneAwareModel):
    case_id: str
    target_consumer_id: str
    tool: FinancialTool
    requested_data: list[FinancialDataType] = Field(min_length=1)
    requested_at: datetime


class BehaviorRiskRequest(ContractModel):
    request_id: str
    agent_id: str
    agent_run_id: str
    history: list[CompletedBehaviorEvent]
    current_attempt: CurrentToolCallAttempt


class BehaviorRiskResponse(ContractModel):
    behavior_risk: float = Field(ge=0.0, le=1.0)
    behavior_risk_level: BehaviorRiskLevel
    is_anomaly: bool
    raw_score: float
    history_status: HistoryStatus
    feature_version: str
    model_version: str
