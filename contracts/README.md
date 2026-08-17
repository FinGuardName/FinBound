# Contract freeze

서비스 간 계약은 `docs/04-api-contract.md`와 `docs/06-common-conventions.md`가 기준입니다. 구현 전 아래 DTO/Enum을 합의하고, 이후 변경은 문서와 소비자 테스트를 같은 PR에서 갱신합니다.

## DTO

`EmployeeAuthority`, `ConsumerMandate`, `PermissionTemplate`, `FinancialCase`, `TaskPassport`, `AgentRun`, `ToolCallAttempt`, `ExecutionOutcome`, `ScopeStatus`, `AiRiskResult`, `AuthorizationContext`, `PolicyDecision`, `AuditEvent`

## 불변식

- JSON은 camelCase, Enum은 UPPER_SNAKE_CASE입니다.
- Timestamp는 ISO 8601 timezone 포함 형식입니다.
- Gateway가 검증한 Agent Identity와 서버 저장 Context만 신뢰합니다.
- ScopeStatus는 Core API, PolicyDecision은 OPA의 책임입니다.
- Risk Engine은 권한을 부여하거나 Decision을 반환하지 않습니다.
- 원문 Prompt와 금융 응답 Payload는 Audit 계약에 포함하지 않습니다.
