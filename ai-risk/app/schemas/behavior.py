from datetime import datetime
from enum import StrEnum

from pydantic import BaseModel, ConfigDict, Field
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


class CompletedBehaviorEvent(ContractModel):
    request_id: str
    case_id: str
    target_consumer_id: str
    tool: str
    requested_at: datetime
    decision: Decision
    success: bool
    latency_ms: int = Field(ge=0)
    requested_data: list[str] = Field(default_factory=list)


class CurrentToolCallAttempt(ContractModel):
    case_id: str
    target_consumer_id: str
    tool: str
    requested_data: list[str] = Field(min_length=1)
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
