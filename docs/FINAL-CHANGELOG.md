# FinGuard Final MVP — 변경 요약

## 마지막 수정본에서 유지한 개선점

- `ToolCallAttempt`와 `ExecutionOutcome` 분리
- Gateway Credential 기반 `Verified Agent Identity`
- 서버 저장형 Task Passport와 만료/상태 검증
- Scope Status와 OPA PolicyDecision 분리
- Prompt 원문 Audit 미저장
- Isolation Forest Calibration과 재현성 규칙
- ALLOW/BLOCK/ERROR Audit 구분
- Request ID / Idempotency / Fail-closed
- Dashboard Read-only 원칙
- Docker 기반 재현 가능한 실행환경

## 복구한 핵심 방향

- `Employee Authority`를 Agent 권한의 상한선으로 복구
- `Agent Effective Permission ⊆ Employee Authority` Core Invariant 복구
- Permission Template을 Employee Authority 대체재가 아니라 업무별 제약조건으로 사용
- Consumer Mandate를 P0 Seed Data로 복구
- `Employee Authority vs Agent Effective Permission` UI 복구
- 최초 4인 역할분담 복구
- Vue 3 + Spring Boot + Spring Cloud Gateway + FastAPI Risk Engine 구조 복구

## 수정한 AI 정책

기존 마지막 수정본:

```text
Behavior Risk 고위험 단독
→ ALLOW + riskFlagged=true

Behavior Risk 고위험 + Hard Limit/Scope 위반
→ BLOCK
```

최종안:

```text
behaviorRisk < alertThreshold
→ ALLOW

alertThreshold <= behaviorRisk < criticalThreshold
→ ALLOW + riskFlagged=true

behaviorRisk >= criticalThreshold
→ AI 단독 BLOCK 가능

Hard Limit 초과
→ AI와 무관하게 Rule 기반 BLOCK
```

이를 통해 `Scope/Rule은 모두 정상인데 Isolation Forest가 실제 Runtime 차단에 기여하는 시나리오`를 별도 검증한다.

## 구현 범위 조정

### P0

- Employee Authority
- Permission Template
- Financial Case
- Consumer Mandate Seed
- Task Passport / Effective Permission
- LoanAgent
- Gateway / Verified Identity
- Scope Status
- OPA ALLOW/BLOCK
- Prompt Injection Detection
- Isolation Forest Behavior Detection
- Mock Financial API
- PostgreSQL Audit
- Vue Dashboard
- Docker Compose

### P1

- Consumer Mandate CRUD
- PII / Response Inspection
- MASK / APPROVAL / Human Approval
- Kubernetes NetworkPolicy / RBAC / ServiceAccount
- OpenSearch / Risk History

## 아키텍처 정리

- Dashboard는 PostgreSQL을 직접 조회하지 않고 Spring API를 통해 조회한다.
- Scope 비교는 Financial Context Resolver의 Single Source of Truth다.
- OPA는 Scope를 다시 비교하지 않고 `Scope Status + AI Risk + Hard Limit`을 정책으로 조합해 최종 Decision을 만든다.
- Kubernetes는 메인 FinGuard 기능이 아니라 Gateway 우회 방지를 강화하는 P1 Deployment Hardening으로 분리한다.
