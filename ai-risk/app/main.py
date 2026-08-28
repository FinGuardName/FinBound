from typing import Annotated

from fastapi import Depends, FastAPI, HTTPException

from app.behavior.service import BehaviorModelError, BehaviorRiskService
from app.schemas.behavior import BehaviorRiskRequest, BehaviorRiskResponse
from app.security import internal_credential_is_configured, verify_internal_credential

app = FastAPI(
    title="FinBound AI Risk Engine",
    version="0.1.0",
    description="Returns risk signals only; authorization decisions belong to OPA.",
)
behavior_service = BehaviorRiskService()


@app.get("/health", tags=["operations"])
def health() -> dict[str, str]:
    return {"status": "UP"}


@app.get("/ready", tags=["operations"])
def ready() -> dict[str, str]:
    try:
        if not internal_credential_is_configured():
            raise BehaviorModelError("Internal credential is not configured")
        behavior_service.check_ready()
    except BehaviorModelError as error:
        raise HTTPException(status_code=503, detail="BEHAVIOR_RISK_UNAVAILABLE") from error
    return {"status": "READY"}


@app.post(
    "/internal/v1/risk/behavior",
    response_model=BehaviorRiskResponse,
    response_model_by_alias=True,
    tags=["risk"],
)
def evaluate_behavior(
    request: BehaviorRiskRequest,
    _internal_credential: Annotated[None, Depends(verify_internal_credential)],
) -> BehaviorRiskResponse:
    try:
        return behavior_service.evaluate(request)
    except BehaviorModelError as error:
        raise HTTPException(status_code=503, detail="BEHAVIOR_RISK_UNAVAILABLE") from error
