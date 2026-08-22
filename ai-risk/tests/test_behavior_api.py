from datetime import UTC, datetime, timedelta

from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)


def _event(index: int, now: datetime, interval_seconds: int = 60) -> dict[str, object]:
    return {
        "requestId": f"REQ-{index:03d}",
        "caseId": "LOAN-2026-001",
        "targetConsumerId": "CUST-1001",
        "tool": "CREDIT_SCORE_READ",
        "requestedData": ["CREDIT_SCORE"],
        "requestedAt": (now - timedelta(seconds=(index + 1) * interval_seconds)).isoformat(),
        "decision": "ALLOW",
        "success": True,
        "latencyMs": 100,
    }


def _request(history: list[dict[str, object]], now: datetime) -> dict[str, object]:
    return {
        "requestId": "REQ-CURRENT",
        "agentId": "LOAN-AGENT-01",
        "agentRunId": "RUN-001",
        "history": history,
        "currentAttempt": {
            "caseId": "LOAN-2026-001",
            "targetConsumerId": "CUST-1001",
            "tool": "CREDIT_SCORE_READ",
            "requestedData": ["CREDIT_SCORE"],
            "requestedAt": now.isoformat(),
        },
    }


def test_behavior_endpoint_returns_contract_and_ready_status() -> None:
    now = datetime(2026, 8, 17, 14, 0, tzinfo=UTC)
    response = client.post(
        "/internal/v1/risk/behavior",
        json=_request([_event(index, now) for index in range(6)], now),
    )

    assert response.status_code == 200
    payload = response.json()
    assert 0 <= payload["behaviorRisk"] <= 1
    assert payload["behaviorRiskLevel"] in {"LOW", "ALERT", "CRITICAL"}
    assert payload["historyStatus"] == "READY"
    assert payload["featureVersion"] == "behavior-features-1"
    assert payload["modelVersion"] == "iforest-1"
    assert "decision" not in payload


def test_behavior_endpoint_reports_cold_start() -> None:
    now = datetime(2026, 8, 17, 14, 0, tzinfo=UTC)
    response = client.post("/internal/v1/risk/behavior", json=_request([], now))

    assert response.status_code == 200
    assert response.json()["historyStatus"] == "COLD_START"


def test_behavior_endpoint_rejects_future_outcome_fields() -> None:
    now = datetime(2026, 8, 17, 14, 0, tzinfo=UTC)
    payload = _request([], now)
    payload["currentAttempt"]["success"] = True

    response = client.post("/internal/v1/risk/behavior", json=payload)

    assert response.status_code == 422


def test_rapid_after_hours_pattern_is_critical() -> None:
    now = datetime(2026, 8, 17, 23, 0, tzinfo=UTC)
    history = [_event(index, now, interval_seconds=2) for index in range(18)]

    response = client.post("/internal/v1/risk/behavior", json=_request(history, now))

    assert response.status_code == 200
    assert response.json()["behaviorRiskLevel"] == "CRITICAL"
