# Gateway

Backend 2 소유 영역입니다.

## 책임

- Runtime Tool Call Interception
- Service Credential 기반 Verified Agent Identity
- Request/Trace ID, Idempotency
- Context/Risk/OPA Client와 AuthorizationContext
- `ALLOW/BLOCK` Enforcement와 Fail-closed

Rego가 raw Consumer/Tool/Data를 다시 비교하지 않도록 Core API의 `ScopeStatus`만 정책 입력에 사용합니다.
