# FinGuard MVP 테스트·데모 시나리오 — 2026.08.17 회의 반영

## 1. 테스트 계층

```text
Unit Test
→ Scope 계산 / Feature Builder / Rego Rule

Contract Test
→ Core ↔ Gateway ↔ FastAPI ↔ OPA DTO

Integration Test
→ Gateway / Core Context / Risk / OPA / Mock Finance / Audit

E2E Test
→ Vue / AgentRun / LoanAgent / Dashboard

P1 Infrastructure Test
→ Kubernetes NetworkPolicy / RBAC
```

P0 Release Gate는 Unit + Contract + Integration + E2E + Docker Compose 재현이다.

---

## 2. 기본 Seed

```text
EMP-101
→ CUST-1001 / CUST-9999 모두 조회 권한 있음

Current Case
→ CUST-1001 / LOAN_REVIEW

PASS-001
→ Agent=LOAN-AGENT-01
→ Consumer=CUST-1001
→ CREDIT_SCORE_READ / INCOME_READ / DEBT_READ
```

---

## 3. 정상 ALLOW

Given:

```text
Verified Agent = OK
모든 Scope = OK
Prompt Risk = LOW
Behavior Risk = LOW
Hard Limit = false
```

When:

```text
CREDIT_SCORE_READ(CUST-1001)
```

Then:

```text
OPA = ALLOW
Mock Finance 호출 = 1회
Business Audit = COMPLETED / ALLOW
```

---

## 4. 핵심 최소권한 — Employee는 가능하지만 Case 밖 고객

```text
EMP-101 Employee Authority
→ CUST-9999 가능

Current Case
→ CUST-1001

Request
→ CREDIT_SCORE_READ(CUST-9999)
```

Expected:

```text
employeeAuthority = OK
customerScope = VIOLATION
OPA = BLOCK
Reason = CASE_SCOPE_VIOLATION
Mock Finance 호출 = 0회
```

---

## 5. 인증 성공 후 Business Audit 시작

Given: Valid Agent Credential.

Expected ordering:

```text
Size / Envelope
→ Rate Limit
→ Credential Verified
→ PROCESSING Business Audit
→ Context / Risk / OPA
```

검증:

- 인증 전 Business Audit row가 생성되지 않는다.
- 인증 성공 이후에만 Business AuditEvent가 생성된다.

---

## 6. 인증 실패 SecurityAuthEvent

Given: Invalid Agent Credential.

Then:

```text
Business AuditEvent 생성 = 0
SecurityAuthEvent 생성 = 1
Authorization / OPA 호출 = 0
Mock Finance 호출 = 0
```

SecurityAuthEvent에 Prompt/Document/금융 원문이 없는지 확인한다.

---

## 7. Audit Flood 기본 방어

Given: 동일 Source에서 인증 실패 요청을 대량 발생.

Then:

```text
Request Size Limit / Rate Limit
→ Authentication / Security Event보다 앞단에서 동작
```

P0에서는 최소 Rate Limit이 작동하는지만 확인하고 대규모 부하 성능을 과장하지 않는다.

---

## 8. Gateway DB 직접 접근 금지

목적: Architecture Freeze 검증.

- Gateway Module에 JPA Repository/DB Credential이 없어야 한다.
- Context는 Core `/internal/v1/context/resolve`를 통해 얻는다.
- Audit은 Core Audit API를 통해 저장한다.
- Behavior History는 Core History API를 통해 얻는다.

통합 테스트에서 Core Mock을 끄면 Gateway가 DB로 우회 조회하지 않고 Fail-closed되어야 한다.

---

## 9. Prompt Injection — 새 입력 검사

Given:

```text
AgentRun에 새 DOC-002 추가
DOC-002 = 악성 Prompt Injection 포함
```

Then:

```text
Prompt Detector 호출 = 1회
PromptRiskSnapshot 생성
inputHash / modelVersion 기록
```

---

## 10. Prompt Risk Snapshot 재사용

Given:

```text
INPUT-001 Hash 동일
Prompt Model Version 동일
```

When: 동일 AgentRun에서 Tool Call 3회.

Then:

```text
Prompt Detector 추가 호출 = 0회
세 Runtime 판단에서 동일 PromptRiskSnapshot 사용
Behavior Risk는 각 Tool Call마다 새 계산
```

---

## 11. 새 Prompt / Document 재검사

Given: AgentRun 중 새로운 DOC-003 유입 또는 Prompt 변경.

Then:

```text
새 inputHash 생성
Prompt Detector 재호출
새 PromptRiskSnapshot 저장
```

---

## 12. Prompt Detector Miss + Case Rule

Given:

```text
Prompt Detector = LOW / Miss
Request = CUST-9999
Current Case = CUST-1001
```

Then:

```text
customerScope = VIOLATION
→ CASE_SCOPE_VIOLATION
→ BLOCK
→ Downstream 0회
```

---

## 13. Tool / Data / Mandate 위반

각 Scope를 독립적으로 `VIOLATION`으로 만들어 OPA Reason Code와 Downstream 0회를 확인한다.

```text
TOOL_SCOPE_VIOLATION
DATA_SCOPE_VIOLATION
MANDATE_SCOPE_VIOLATION
```

---

## 14. Passport / Identity

- 만료 Passport → `TASK_PASSPORT_EXPIRED`, BLOCK
- Verified Agent와 Passport Agent 불일치 → `AGENT_IDENTITY_MISMATCH`, BLOCK

---

## 15. Behavior Alert

```text
모든 Scope = OK
Hard Limit = false
alertThreshold <= behaviorRisk < criticalThreshold
```

Then:

```text
ALLOW
riskFlagged = true
```

---

## 16. Behavior Critical — AI 독립 가치

```text
모든 Scope = OK
같은 Case / Consumer
허용 Tool / Data
Hard Limit = false
behaviorRisk >= criticalThreshold
```

Then:

```text
BEHAVIOR_ANOMALY
BLOCK
Downstream 0회
```

Rule Only 비교 시 ALLOW, FinGuard + AI에서 BLOCK되는 인과관계를 보여준다.

---

## 17. Hard Limit

```text
requestCount1m > hardRequestLimit1m
→ HARD_REQUEST_LIMIT_EXCEEDED
→ AI Score와 무관하게 BLOCK
```

---

## 18. Behavior Feature의 미래값 금지

현재 ToolCallAttempt에 다음을 넣지 않는다.

```text
success
recordsRead
latencyMs
```

이 값은 과거 완료 ExecutionOutcome에서만 Feature에 사용한다.

---

## 19. Behavior History 경로

Given: Gateway가 Behavior Risk 계산 필요.

Expected:

```text
Gateway
→ Core Behavior History API
→ 최근 완료 Events
→ Current ToolCallAttempt 결합
→ FastAPI
```

Gateway/FastAPI DB 직접 조회 = 0.

---

## 20. AI Risk / OPA Fail-closed

- Behavior Risk Timeout → `BEHAVIOR_RISK_UNAVAILABLE`, BLOCK
- 필요한 Prompt Snapshot 없음/오류 → `PROMPT_RISK_UNAVAILABLE`, BLOCK
- OPA Timeout → `POLICY_ENGINE_UNAVAILABLE`, BLOCK
- Core Context 실패 → `CONTEXT_SERVICE_UNAVAILABLE`, BLOCK

Downstream은 모두 0회다.

---

## 21. Business Audit 선저장 실패

Given: 인증 성공 후 Core Audit API가 PROCESSING 저장 실패.

Then:

```text
AUDIT_WRITE_FAILED
Authorization/Downstream 중단
Mock Finance 호출 = 0회
```

---

## 22. Mock Finance 오류

OPA ALLOW 이후 Mock Finance Timeout.

```text
downstreamReached = true
responseReleased = false
Audit Status = ERROR
Reason = DOWNSTREAM_TIMEOUT
```

---

## 23. Request Idempotency

동일 Request ID 2회:

```text
실제 Downstream 실행 최대 1회
동일 결과 재사용 또는 DUPLICATE_REQUEST
```

---

## 24. Dashboard

ALLOW / BLOCK / ERROR를 모두 만든 뒤 다음을 확인한다.

- Scope Status
- Prompt Risk Snapshot / Model Version
- Behavior Risk / Feature Version
- OPA Policy Version
- Reason Code
- downstreamReached
- LoanAgent 실행 화면에 통합된 Authority vs Effective Permission
- 원본 Prompt/금융 응답 미표시
- Vue DB 직접 접근 없음

SecurityAuthEvent를 Dashboard에 같이 보여줄지는 P0 UI 범위에서 선택하되 DB에는 저장한다.

---

## 25. Docker Compose E2E

대상:

```text
Spring Core
Spring Cloud Gateway
LoanAgent
Mock Financial API
FastAPI AI Risk Engine
OPA
PostgreSQL
Vue
```

필수:

- 정상 ALLOW
- Case BLOCK
- 인증 실패 Security Event
- Prompt 새 입력 검사 / Snapshot 재사용
- Behavior Critical BLOCK
- Dashboard

---

## 26. P1 Kubernetes 우회 방지 테스트

**P0 Release Gate에 포함하지 않는다.**

Kubernetes 적용 후:

```text
LoanAgent → Gateway      ALLOW
LoanAgent → Mock Finance DENY
LoanAgent → PostgreSQL   DENY
Gateway   → PostgreSQL   DENY
Core      → PostgreSQL   ALLOW
```

ServiceAccount의 불필요한 Kubernetes API 접근도 거부한다.

---

## 27. 최종 데모 체크리스트

- [ ] EMP-101 넓은 Authority 표시
- [ ] CUST-1001 Case / Passport
- [ ] 정상 ALLOW
- [ ] CUST-9999 → employeeAuthority OK / customerScope VIOLATION
- [ ] `CASE_SCOPE_VIOLATION` / Downstream 0회
- [ ] 인증 실패 → Business Audit 없음 / SecurityAuthEvent 있음
- [ ] Gateway DB 직접 접근 없음
- [ ] 새 Prompt/Document Prompt Detector 실행
- [ ] 동일 입력 Tool Call에서 Snapshot 재사용
- [ ] Prompt Detector Miss에서도 Case Rule BLOCK
- [ ] Behavior Alert
- [ ] Behavior Critical AI-only BLOCK
- [ ] Hard Limit Rule BLOCK
- [ ] ALLOW/BLOCK/ERROR Dashboard
- [ ] Docker Compose E2E
- [ ] Kubernetes 우회 방지는 P1로 설명
