from datetime import UTC, datetime, timedelta

import numpy as np

from app.feature_builder import FEATURE_NAMES, build_feature_vector
from app.schemas.behavior import CompletedBehaviorEvent, CurrentToolCallAttempt


def test_feature_builder_uses_only_completed_past_events() -> None:
    now = datetime(2026, 8, 17, 14, 0, tzinfo=UTC)
    history = [
        CompletedBehaviorEvent(
            requestId="REQ-OLD",
            caseId="CASE-1",
            targetConsumerId="CUST-1",
            tool="CREDIT_SCORE_READ",
            requestedData=["CREDIT_SCORE"],
            requestedAt=now - timedelta(seconds=30),
            decision="ALLOW",
            success=True,
            latencyMs=100,
        ),
        CompletedBehaviorEvent(
            requestId="REQ-FUTURE",
            caseId="CASE-2",
            targetConsumerId="CUST-2",
            tool="DEBT_READ",
            requestedData=["DEBT"],
            requestedAt=now + timedelta(seconds=1),
            decision="BLOCK",
            success=False,
            latencyMs=100,
        ),
    ]
    current = CurrentToolCallAttempt(
        caseId="CASE-1",
        targetConsumerId="CUST-1",
        tool="INCOME_READ",
        requestedData=["INCOME"],
        requestedAt=now,
    )

    values = dict(zip(FEATURE_NAMES, build_feature_vector(history, current), strict=True))

    assert values["requestCount1m"] == 2
    assert values["uniqueCustomers5m"] == 1
    assert values["uniqueTools5m"] == 2
    assert values["blockRatio5m"] == 0
    assert values["errorRatio5m"] == 0
    assert values["averageRequestIntervalMs"] == 30_000
    assert np.all(np.isfinite(list(values.values())))
