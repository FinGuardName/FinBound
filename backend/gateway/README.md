# Gateway

Backend 2 소유 영역입니다.

## 책임

- Runtime Tool Call Interception
- Service Credential 기반 Verified Agent Identity
- Request/Trace ID, Idempotency
- Context/Risk/OPA Client와 AuthorizationContext
- Core Context/Audit/Security/Behavior History Client
- `ALLOW/BLOCK` Enforcement와 Fail-closed

Gateway는 FinGuard PostgreSQL에 직접 접근하지 않습니다. 인증 성공 후 Core API로 Business Audit을 생성하고, 인증 실패는 최소 SecurityAuthEvent로 분리해 저장합니다. Rego가 raw Consumer/Tool/Data를 다시 비교하지 않도록 Core API의 `ScopeStatus`만 정책 입력에 사용합니다.
