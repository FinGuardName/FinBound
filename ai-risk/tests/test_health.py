import pytest
from fastapi.testclient import TestClient

from app.main import app, prompt_service
from app.prompt.model import PromptModelError


def test_health() -> None:
    response = TestClient(app).get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "UP"}


def test_ready_checks_model_and_internal_credential(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("FINGUARD_INTERNAL_CREDENTIAL", "test-internal-credential")
    monkeypatch.setattr(prompt_service, "check_ready", lambda: None)

    response = TestClient(app).get("/ready")

    assert response.status_code == 200
    assert response.json() == {"status": "READY"}


def test_ready_fails_when_internal_credential_is_missing(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.delenv("FINGUARD_INTERNAL_CREDENTIAL", raising=False)

    response = TestClient(app).get("/ready")

    assert response.status_code == 503
    assert response.json()["detail"] == "BEHAVIOR_RISK_UNAVAILABLE"


def test_ready_fails_closed_when_prompt_model_is_unavailable(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setenv("FINGUARD_INTERNAL_CREDENTIAL", "test-internal-credential")

    def fail_readiness() -> None:
        raise PromptModelError("model unavailable")

    monkeypatch.setattr(prompt_service, "check_ready", fail_readiness)

    response = TestClient(app).get("/ready")

    assert response.status_code == 503
    assert response.json()["detail"] == "PROMPT_RISK_UNAVAILABLE"
