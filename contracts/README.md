# Contract freeze

현재 서비스 간 계약은 `docs/04-api-contract.md`와 `docs/06-common-conventions.md`가 기준입니다. OpenAPI가 Freeze되는 시점부터 `docs/api-contract.yaml`을 추가합니다. 구현 전 아래 DTO/Enum을 합의하고, 이후 변경은 문서와 소비자 테스트를 같은 PR에서 갱신합니다.

`contracts/audit`에는 P0 Audit Runtime Event JSON Schema와 검증 Fixture가 있습니다. 문서와 Schema가 충돌하면 위 두 문서가 우선하며, 계약 변경은 문서와 생산자·소비자 테스트를 같은 PR에서 갱신합니다.

## DTO

`EmployeeAuthority`, `ConsumerMandate`, `PermissionTemplate`, `FinancialCase`, `TaskPassport`, `AgentRun`, `ToolCallAttempt`, `ExecutionOutcome`, `ScopeStatus`, `PromptRiskSnapshot`, `AiRiskResult`, `AuthorizationContext`, `PolicyDecision`, `AuditEvent`, `SecurityAuthEvent`, `BehaviorHistory`

## 불변식

- JSON은 camelCase, Enum은 UPPER_SNAKE_CASE입니다.
- Timestamp는 ISO 8601 timezone 포함 형식입니다.
- Gateway가 검증한 Agent Identity와 서버 저장 Context만 신뢰합니다.
- Gateway는 PostgreSQL에 직접 접근하지 않고 Core Internal API를 사용합니다.
- ScopeStatus는 Core API, PolicyDecision은 OPA의 책임입니다.
- Risk Engine은 권한을 부여하거나 Decision을 반환하지 않습니다.
- Business Audit은 인증 성공 후 생성하고 인증 실패는 최소 SecurityAuthEvent로 분리합니다.
- 동일한 입력은 PromptRiskSnapshot을 재사용하고 새 입력만 다시 검사합니다.
- 원문 Prompt와 금융 응답 Payload는 Audit 계약에 포함하지 않습니다.
