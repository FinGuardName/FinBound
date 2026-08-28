from datetime import UTC, datetime, timedelta

import pytest
from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)
TEST_CREDENTIAL = "test-internal-credential"
INTERNAL_HEADERS = {"X-FinGuard-Service-Credential": TEST_CREDENTIAL}


@pytest.fixture(autouse=True)
def configure_internal_credential(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("FINGUARD_INTERNAL_CREDENTIAL", TEST_CREDENTIAL)


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
    now = datetime(2026, 8, 17, 5, 0, tzinfo=UTC)
    response = client.post(
        "/internal/v1/risk/behavior",
        json=_request([_event(index, now, interval_seconds=30) for index in range(7)], now),
        headers=INTERNAL_HEADERS,
    )

    assert response.status_code == 200
    payload = response.json()
    assert 0 <= payload["behaviorRisk"] <= 1
    assert payload["behaviorRiskLevel"] == "LOW"
    assert payload["isAnomaly"] is False
    assert payload["historyStatus"] == "READY"
    assert payload["featureVersion"] == "behavior-features-1"
    assert payload["modelVersion"] == "iforest-1"
    assert "decision" not in payload
    assert payload["isAnomaly"] is (payload["behaviorRiskLevel"] != "LOW")


def test_behavior_endpoint_reports_cold_start() -> None:
    now = datetime(2026, 8, 17, 14, 0, tzinfo=UTC)
    response = client.post(
        "/internal/v1/risk/behavior", json=_request([], now), headers=INTERNAL_HEADERS
    )

    assert response.status_code == 200
    assert response.json()["historyStatus"] == "COLD_START"
    assert response.json()["behaviorRiskLevel"] == "LOW"
    assert response.json()["behaviorRisk"] == 0
    assert response.json()["isAnomaly"] is False


def test_behavior_endpoint_uses_filtered_history_for_cold_start() -> None:
    now = datetime(2026, 8, 17, 14, 0, tzinfo=UTC)
    stale_history = [_event(index, now, interval_seconds=360) for index in range(5)]

    response = client.post(
        "/internal/v1/risk/behavior",
        json=_request(stale_history, now),
        headers=INTERNAL_HEADERS,
    )

    assert response.status_code == 200
    assert response.json()["historyStatus"] == "COLD_START"
    assert response.json()["behaviorRiskLevel"] == "LOW"


def test_behavior_endpoint_rejects_future_outcome_fields() -> None:
    now = datetime(2026, 8, 17, 14, 0, tzinfo=UTC)
    payload = _request([], now)
    payload["currentAttempt"]["success"] = True

    response = client.post("/internal/v1/risk/behavior", json=payload, headers=INTERNAL_HEADERS)

    assert response.status_code == 422


def test_rapid_after_hours_pattern_is_critical() -> None:
    now = datetime(2026, 8, 17, 23, 0, tzinfo=UTC)
    history = [_event(index, now, interval_seconds=2) for index in range(18)]

    response = client.post(
        "/internal/v1/risk/behavior", json=_request(history, now), headers=INTERNAL_HEADERS
    )

    assert response.status_code == 200
    assert response.json()["behaviorRiskLevel"] == "CRITICAL"
    assert response.json()["isAnomaly"] is True


def test_legitimate_overtime_pattern_remains_low() -> None:
    now = datetime(2026, 8, 17, 11, 0, tzinfo=UTC)
    history = [_event(index, now, interval_seconds=30) for index in range(7)]

    response = client.post(
        "/internal/v1/risk/behavior", json=_request(history, now), headers=INTERNAL_HEADERS
    )

    assert response.status_code == 200
    assert response.json()["behaviorRiskLevel"] == "LOW"


def test_rapid_business_hours_pattern_reaches_alert() -> None:
    now = datetime(2026, 8, 17, 5, 0, tzinfo=UTC)
    history = [_event(index, now, interval_seconds=8) for index in range(12)]

    response = client.post(
        "/internal/v1/risk/behavior", json=_request(history, now), headers=INTERNAL_HEADERS
    )

    assert response.status_code == 200
    assert response.json()["behaviorRiskLevel"] == "ALERT"
    assert response.json()["isAnomaly"] is True


def test_behavior_endpoint_rejects_unknown_tool_and_data() -> None:
    now = datetime(2026, 8, 17, 14, 0, tzinfo=UTC)
    payload = _request([], now)
    payload["currentAttempt"]["tool"] = "UNDECLARED_TOOL"
    payload["currentAttempt"]["requestedData"] = ["UNDECLARED_DATA"]

    response = client.post("/internal/v1/risk/behavior", json=payload, headers=INTERNAL_HEADERS)

    assert response.status_code == 422


def test_behavior_endpoint_rejects_timestamp_without_timezone() -> None:
    now = datetime(2026, 8, 17, 14, 0, tzinfo=UTC)
    payload = _request([], now)
    payload["currentAttempt"]["requestedAt"] = "2026-08-17T14:00:00"

    response = client.post("/internal/v1/risk/behavior", json=payload, headers=INTERNAL_HEADERS)

    assert response.status_code == 422


def test_behavior_endpoint_requires_internal_credential() -> None:
    now = datetime(2026, 8, 17, 14, 0, tzinfo=UTC)

    missing = client.post("/internal/v1/risk/behavior", json=_request([], now))
    invalid = client.post(
        "/internal/v1/risk/behavior",
        json=_request([], now),
        headers={"X-FinGuard-Service-Credential": "invalid"},
    )
    assert missing.status_code == 401
    assert missing.json()["detail"] == "INTERNAL_CREDENTIAL_INVALID"
    assert invalid.status_code == 401
    assert invalid.json()["detail"] == "INTERNAL_CREDENTIAL_INVALID"


def test_behavior_endpoint_fails_closed_when_server_credential_is_missing(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.delenv("FINGUARD_INTERNAL_CREDENTIAL")
    now = datetime(2026, 8, 17, 14, 0, tzinfo=UTC)

    response = client.post(
        "/internal/v1/risk/behavior", json=_request([], now), headers=INTERNAL_HEADERS
    )

    assert response.status_code == 503
    assert response.json()["detail"] == "BEHAVIOR_RISK_UNAVAILABLE"
