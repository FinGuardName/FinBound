# Audit

Backend 3이 Schema와 Runtime Event Contract를 주도하고 Backend 1 Core가 저장을 담당하는 공통 영역입니다. `ToolCallAttempt`와 `ExecutionOutcome`을 분리하고 인증 성공 후 생성한 Business AuditEvent를 `PROCESSING → COMPLETED | ERROR`로 완성합니다. 인증 실패는 Business Audit이 아닌 최소 `SecurityAuthEvent`로 기록합니다.

저장: 식별자, ScopeStatus, Risk/Version, PolicyDecision, downstream/response 상태, Reason Code.

저장 금지: 원본 Prompt, 금융 응답 Payload, 실제 개인정보, Credential, Secret.

## 현재 구현 범위

이 모듈에는 DB Entity나 저장 API가 없습니다. `contracts/audit`의 JSON Schema와 Fixture를 실제 검증하는 소비자 계약 테스트만 있습니다.

| Contract | 의미 |
|---|---|
| `ToolCallAttempt` | Risk 평가 전 현재 요청. `success`, `recordsRead`, `latencyMs` 같은 미래값 금지 |
| `ExecutionOutcome` | Policy Decision과 시스템 실행 결과를 분리한 최종 결과 |
| `AuditEvent` | 인증 성공 후 생성하는 Business Audit의 `PROCESSING / COMPLETED / ERROR` 상태 |
| `SecurityAuthEvent` | 인증 실패 시 Business Audit 대신 남기는 최소 보안 Event |

검증 실행:

```bash
./gradlew :backend:audit:check
```

## 합의 필요

JSON Schema는 `docs/04-api-contract.md`, `docs/06-common-conventions.md`의 P0 Runtime Contract를
기계가 검사할 수 있게 옮긴 것입니다. 불일치가 발견되면 두 문서가 우선합니다.

- 시스템 장애는 `decision=ERROR`가 아니라 `systemOutcome=ERROR`로 기록합니다.
- OPA 호출 전 장애를 표현하기 위해 ERROR Outcome과 ERROR Audit의 `decision`은 선택값입니다.
- `errorLocation`은 현재 `UPPER_SNAKE_CASE`만 강제합니다. 고정 Enum 목록은 팀 합의 후 추가해야 합니다.
- `AuditEvent`는 Core의 저장 상태를 표현합니다. `POST /internal/v1/audits`, `PATCH .../outcome` 요청 DTO를 별도 Schema로 분리할지는 Backend1·2와 합의해야 합니다.
- 알려지지 않은 필드와 민감 원문 필드는 `additionalProperties: false`로 거부합니다.
- ALLOW 완료 Audit은 `success=true`와 실행 측정값을 저장하며, BLOCK Audit에는 실행 측정값을
  넣지 않습니다.

## 인수 조건 대응

| 인수 조건 | Fixture로 확인하는 내용 |
|---|---|
| AC-01 | ALLOW, Downstream 도달, 응답 반환, COMPLETED Audit |
| AC-02 | CASE_SCOPE_VIOLATION, BLOCK, Downstream 미도달, COMPLETED Audit |
| AC-10 | 인증 실패는 최소 SecurityAuthEvent이며 Business Audit/민감 필드가 없음 |
| AC-17 | Downstream 오류는 Decision ERROR가 아닌 ERROR Outcome/Audit |
