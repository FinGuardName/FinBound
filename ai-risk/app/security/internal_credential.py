import os
from secrets import compare_digest
from typing import Annotated

from fastapi import Header, HTTPException

INTERNAL_CREDENTIAL_ENV = "FINGUARD_INTERNAL_CREDENTIAL"
INTERNAL_CREDENTIAL_HEADER = "X-FinGuard-Service-Credential"


def internal_credential_is_configured() -> bool:
    return bool(os.getenv(INTERNAL_CREDENTIAL_ENV))


def verify_internal_credential(
    credential: Annotated[str | None, Header(alias=INTERNAL_CREDENTIAL_HEADER)] = None,
) -> None:
    expected = os.getenv(INTERNAL_CREDENTIAL_ENV)
    if not internal_credential_is_configured() or expected is None:
        raise HTTPException(status_code=503, detail="BEHAVIOR_RISK_UNAVAILABLE")
    if credential is None or not compare_digest(
        credential.encode("utf-8"), expected.encode("utf-8")
    ):
        raise HTTPException(status_code=401, detail="INTERNAL_CREDENTIAL_INVALID")
