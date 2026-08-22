from fastapi import FastAPI, HTTPException

from app.behavior.service import BehaviorModelError, BehaviorRiskService
from app.schemas.behavior import BehaviorRiskRequest, BehaviorRiskResponse

app = FastAPI(
    title="FinGuard AI Risk Engine",
    version="0.1.0",
    description="Returns risk signals only; authorization decisions belong to OPA.",
)
behavior_service = BehaviorRiskService()


@app.get("/health", tags=["operations"])
def health() -> dict[str, str]:
    return {"status": "UP"}


@app.post(
    "/internal/v1/risk/behavior",
    response_model=BehaviorRiskResponse,
    response_model_by_alias=True,
    tags=["risk"],
)
def evaluate_behavior(request: BehaviorRiskRequest) -> BehaviorRiskResponse:
    try:
        return behavior_service.evaluate(request)
    except BehaviorModelError as error:
        raise HTTPException(status_code=503, detail="BEHAVIOR_RISK_UNAVAILABLE") from error
