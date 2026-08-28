# FinGuard 공통 규칙

## 1. JSON / 코드 명명

```text
JSON Field      → camelCase
Java Class      → PascalCase
Java Variable   → camelCase
Python Variable → snake_case
DB Table        → snake_case
Enum Value      → UPPER_SNAKE_CASE
```

서비스 간 JSON Contract는 `camelCase`를 사용한다.

---

## 2. 식별자

예시 형식:

```text
Employee       EMP-101
Consumer       CUST-1001
Case           LOAN-2026-001
Passport       PASS-001
Agent          LOAN-AGENT-01
AgentRun       RUN-001
AuditEvent     AUD-001
SecurityEvent  SEC-001
InputRisk      PRS-001
Permission     LOAN_REVIEW_STANDARD
InputRef       INPUT-001
Request ID     UUID
Trace ID       W3C Trace ID 또는 UUID
```

ID는 의미를 식별하기 위한 값이며 인증수단으로 사용하지 않는다.

---

## 3. 시간

모든 Timestamp는 Timezone을 포함한다.

```text
2026-08-17T14:01:00+09:00
```

서버 내부 저장/비교에서 시간대 혼용을 피한다.

---

## 4. Employee Authority Status

```text
ACTIVE
INACTIVE
```

Agent 권한은 ACTIVE Employee Authority를 초과할 수 없다.

---

## 5. Consumer Mandate Status

```text
ACTIVE
REVOKED
EXPIRED
```

P0에서는 ACTIVE Seed Data를 사용한다.

---

## 6. Permission Template Status

```text
ACTIVE
INACTIVE
```

---

## 7. Financial Case Status

```text
ACTIVE
COMPLETED
EXPIRED
CANCELLED
```

---

## 8. Task Passport Status

```text
ACTIVE
EXPIRED
REVOKED
STALE
```

---

## 9. AgentRun Status

```text
CREATED
RUNNING
COMPLETED
FAILED
```

---

## 10. Audit / Security Event Status

### Business AuditEvent

```text
PROCESSING
COMPLETED
ERROR
```

`PolicyDecision=BLOCK`이 정상적으로 집행되면 `AuditStatus=COMPLETED`다.

### SecurityAuthEvent

인증·인가 실패 등 Gateway/Core API 보안 Event는 Business Audit과 분리한다.

```text
AUTH_FAILURE
RATE_LIMITED       # 필요 시 집계/운영 로그로 사용
```

Core `/api/v1/**`의 Credential·Role·Employee 검증 실패도 `AUTH_FAILURE`로 기록하고
§20의 구체적인 Reason Code로 원인을 구분한다.

Business AuditEvent는 **Agent 인증 성공 이후** 생성한다.

## 11. Policy Decision

P0:

```text
ALLOW
BLOCK
```

P1:

```text
MASK
APPROVAL
```

시스템 장애는 Decision Enum에 `ERROR`를 추가하지 않고 Audit/System Outcome으로 표현한다.

---

## 12. Severity

```text
LOW
MEDIUM
HIGH
CRITICAL
```

Severity와 Decision은 동일하지 않다.

예:

```text
Behavior Alert
→ Severity=HIGH
→ Decision=ALLOW
→ riskFlagged=true
```

---

## 13. Scope Status

```text
OK
VIOLATION
```

Scope Status 대상:

```text
employeeAuthority
permissionTemplate
caseStatus
mandate
passportStatus
agentBinding
customerScope
toolScope
dataScope
```

### 책임 규칙

```text
Financial Context Resolver
→ Scope Status 계산

OPA
→ Scope Status를 정책 입력으로 사용
→ PolicyDecision 계산
```

동일 Scope 비교를 Spring과 Rego에 중복 구현하지 않는다.

---

## 14. History Status

```text
READY
COLD_START
```

`COLD_START`는 서버 재시작 여부가 아니라 Behavior 판단에 필요한 최소 행동 이력이 부족함을 의미한다.

---

## 15. Behavior Risk Level

```text
LOW
ALERT
CRITICAL
```

의미:

```text
LOW
→ behaviorRisk < alertThreshold

ALERT
→ alertThreshold <= behaviorRisk < criticalThreshold

CRITICAL
→ behaviorRisk >= criticalThreshold
```

`behaviorRisk`는 공격 확률이라고 표현하지 않는다.

---

## 16. Tool Enum

```text
CREDIT_SCORE_READ
INCOME_READ
DEBT_READ
```

P0에서 문자열 자유입력을 허용하지 않는다.

---

## 17. Data Type Enum

```text
CREDIT_SCORE
INCOME
DEBT
```

P1 확장 예:

```text
TRANSACTION_HISTORY
ACCOUNT_INFO
```

---

## 18. Task Type

```text
LOAN_REVIEW
```

---

## 19. Prompt Attack Type

```text
IGNORE_PREVIOUS_INSTRUCTION
POLICY_BYPASS
SYSTEM_PROMPT_EXTRACTION
CROSS_CUSTOMER_ACCESS
UNAUTHORIZED_TOOL_REQUEST
UNKNOWN_PROMPT_ATTACK
```

---

## 20. Reason Code

### Request / Identity

| Code | 의미 |
|---|---|
| `INVALID_TOOL_REQUEST` | Tool Call Schema 오류 |
| `AGENT_AUTHENTICATION_FAILED` | Agent Credential 검증 실패 |
| `AGENT_IDENTITY_MISMATCH` | Verified Agent와 Passport Agent 불일치 |
| `CORE_API_CREDENTIAL_INVALID` | Core `/api/v1/**` Bearer Credential 누락 또는 불일치 |
| `CORE_API_ROLE_FORBIDDEN` | 인증된 Core API 호출자의 Role 부족 |
| `EMPLOYEE_IDENTITY_MISMATCH` | 인증된 Operator Employee와 요청 Employee 불일치 |
| `DUPLICATE_REQUEST` | 동일 Request ID 중복 요청 |
| `REQUEST_RATE_LIMITED` | Gateway 요청 제한 초과 |
| `CONTEXT_SERVICE_UNAVAILABLE` | Core Context API 조회 실패 |
| `BEHAVIOR_HISTORY_UNAVAILABLE` | Core Behavior History 조회 실패 |

### Employee / Context / Scope

| Code | 의미 |
|---|---|
| `CONTEXT_NOT_FOUND` | 필요한 Context 조회 실패 |
| `EMPLOYEE_AUTHORITY_INACTIVE` | Employee Authority 비활성 |
| `EMPLOYEE_AUTHORITY_VIOLATION` | Employee Authority 밖 요청 |
| `PERMISSION_TEMPLATE_INACTIVE` | Permission Template 비활성 |
| `PERMISSION_TEMPLATE_VIOLATION` | 업무 Template 밖 요청 |
| `CASE_INACTIVE` | Case 비활성 |
| `CASE_EXPIRED` | Case 만료 |
| `CASE_SCOPE_VIOLATION` | 현재 Case 대상 고객과 요청 고객 불일치 |
| `MANDATE_NOT_FOUND` | Mandate 없음 |
| `MANDATE_INACTIVE` | Mandate 비활성/철회/만료 |
| `MANDATE_SCOPE_VIOLATION` | Consumer Mandate 밖 Data 요청 |
| `TASK_PASSPORT_NOT_FOUND` | Passport 없음 |
| `TASK_PASSPORT_INACTIVE` | Passport 비활성 |
| `TASK_PASSPORT_EXPIRED` | Passport 만료 |
| `TASK_PASSPORT_STALE` | Source Version 불일치 |
| `TOOL_SCOPE_VIOLATION` | 허용되지 않은 Tool |
| `DATA_SCOPE_VIOLATION` | 허용되지 않은 Data |

### AI Risk

| Code | 의미 |
|---|---|
| `PROMPT_INJECTION` | Prompt Injection 차단 조건 충족 |
| `BEHAVIOR_ANOMALY` | Behavior Critical Threshold 충족 |
| `HARD_REQUEST_LIMIT_EXCEEDED` | Deterministic Hard Limit 초과 |
| `PROMPT_RISK_UNAVAILABLE` | Prompt 분석 실패 |
| `BEHAVIOR_RISK_UNAVAILABLE` | Behavior 분석 실패 |

### Policy / Audit / Downstream

| Code | 의미 |
|---|---|
| `POLICY_ENGINE_UNAVAILABLE` | OPA Timeout/오류 |
| `POLICY_DECISION_INVALID` | OPA 응답 형식 오류 |
| `AUDIT_WRITE_FAILED` | Business Audit 저장 실패 |
| `SECURITY_EVENT_WRITE_FAILED` | SecurityAuthEvent 저장 실패 |
| `DOWNSTREAM_ERROR` | Mock Financial API 처리 오류 |
| `DOWNSTREAM_TIMEOUT` | Mock Financial API Timeout |
| `INTERNAL_CREDENTIAL_INVALID` | Gateway 내부 Credential 검증 실패 |

---

## 21. Risk Naming

외부 Contract:

```text
promptRisk
behaviorRisk
```

내부 모델 값:

```text
promptModelScore
isolationRawScore
```

Isolation Forest raw score와 `behaviorRisk`를 명확히 구분한다.

---

## 22. Threshold Naming

```text
promptBlockThreshold
behaviorAlertThreshold
behaviorCriticalThreshold
hardRequestLimit1m
```

Threshold 값은 Config/환경변수/정책 설정에서 단일 관리하고 여러 코드에 Magic Number로 중복하지 않는다.

---

## 23. Version Naming

```text
modelVersion
featureVersion
datasetVersion
policyVersion
templateVersion
```

예:

```text
prompt-guard-1
iforest-1
behavior-features-1
synthetic-agent-log-1
loan-review-policy-1
```

---

## 24. Audit 원문 저장 규칙

### 저장 가능

```text
Employee / Agent / Case / Passport / Consumer 식별자
Tool / Data Type
Input Reference / Hash
Scope Status
Risk Score / Risk Level
Matched Rule ID
PolicyDecision / Reason Code
Downstream / Response 상태
Model / Feature / Policy Version
Timestamp
```

### 저장하지 않음

```text
원본 Prompt
원본 금융 문서
금융 API Response Payload
실제 개인정보 원문
Agent Service Credential
Internal Credential Secret
```

---

## 24.1 DB Ownership 규칙

```text
Core → PostgreSQL O
Gateway / Agent / Frontend / FastAPI / OPA → PostgreSQL X
```

Gateway가 Context, Audit, Security Event, Behavior History가 필요하면 Core Internal API를 호출한다.

---

## 24.2 Prompt Risk Lifecycle 규칙

```text
새 비신뢰 입력
→ Prompt Injection Detection
→ PromptRiskSnapshot

동일 inputHash + modelVersion
→ Tool Call마다 재추론하지 않음

새 Prompt / Document / inputHash 변경
→ 재검사
```

Prompt Risk는 Runtime마다 새로 계산되는 행동 점수가 아니라 **현재 입력 버전에 연결된 Snapshot**이다.

---

## 25. Dashboard 규칙

- Vue Dashboard는 Spring Read-only API만 호출한다.
- PostgreSQL 직접 연결을 금지한다.
- 전체 활동은 `ALLOW / BLOCK / ERROR`를 모두 포함한다.
- 기본 정렬은 `requestedAt DESC`다.
- 목록/상세 조회는 페이지네이션을 사용한다.
- 위험 이벤트는 `riskFlagged=true` 또는 `HIGH/CRITICAL`로 필터링할 수 있다.
- Reason Code는 고정된 사용자 설명과 함께 표시한다.
- LoanAgent 실행 화면의 현재 업무 보호 패널에서 Employee Authority와 Agent Effective Permission 비교를 제공한다.

---

## 26. 로그 규칙

- 구조화 JSON 로그를 사용한다.
- Request ID / Trace ID를 포함한다.
- Credential / Secret / 원문 금융 데이터는 출력하지 않는다.
- Exception Stack은 서버 로그에만 남기고 Agent 응답에 노출하지 않는다.
- `requestedAt / completedAt`은 감사와 Behavior Window 계산에 사용한다.

---

## 27. Scope Status / PolicyDecision 요약 규칙

### Scope Status

```text
"이 요청이 각 권한 범위에 들어오는가?"
→ OK / VIOLATION
```

### PolicyDecision

```text
"이 Scope 상태와 AI Risk를 종합했을 때 실행할 것인가?"
→ ALLOW / BLOCK
```

Spring이 Scope Status를 계산한 뒤 OPA가 동일 비교를 반복하지 않는다.
