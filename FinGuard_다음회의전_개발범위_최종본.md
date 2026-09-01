# FinGuard MVP — 다음 회의 전 개발 범위 최종본

## 1. 목적

회의 마지막에 각 담당자가 **다음 회의 전까지 구현할 최소 범위**를 확정한다.

이번 단계의 목표는 완성 기능을 만드는 것이 아니라, 각 컴포넌트가 **독립적으로 실행 가능한 Skeleton / Mock / Contract**를 준비하여 이후 병렬 개발과 통합이 가능하도록 만드는 것이다.

개발 순서는 다음 방향을 따른다.

```text
Contract Freeze
↓
Independent Mock
↓
Core Authorization
↓
Agent / Gateway / OPA
↓
AI Risk
↓
Frontend Integration
↓
Docker Compose E2E
↓
P1 Hardening
```

핵심 원칙:

> AI 기능부터 만드는 것이 아니라, AI 없이도 동작하는 Financial Case 기반 최소권한 Runtime 차단 구조를 먼저 완성한다.

이번 사이클에서는 Kubernetes 관련 구현을 진행하지 않는다.

```text
P0
→ Docker Compose 기반 MVP 완성

P1
→ 필요 시 Kubernetes NetworkPolicy / ServiceAccount / RBAC Hardening
```

---

# 2. 이번 회의 반영 아키텍처 원칙

## 2.1 Gateway는 FinGuard DB에 직접 접근하지 않는다

```text
Gateway
   ↓ Internal API
FinGuard Core
   ↓
PostgreSQL
```

원칙:

```text
Gateway → PostgreSQL 직접 접근 X
Core    → PostgreSQL 접근 O
```

Gateway가 필요한 Financial Context, Audit 저장, Security Event 저장, Behavior History 조회는 Core의 내부 API를 통해 처리한다.

---

## 2.2 Prompt Injection은 매 Tool Call마다 재검사하지 않는다

Prompt Injection Detection은 **새로운 비신뢰 입력이 유입될 때** 수행한다.

```text
새 Prompt 입력
→ Prompt Injection 검사

새 Document 입력
→ Prompt Injection 검사

새 외부 텍스트 입력
→ Prompt Injection 검사

기존과 동일한 입력
→ 재검사하지 않음
→ 기존 Prompt Risk 재사용
```

Behavior Risk는 행동 이력이 계속 변하므로 **Tool Call마다 새로 계산한다.**

```text
Prompt Risk
→ 입력 생성 / 변경 시 갱신

Behavior Risk
→ Tool Call마다 갱신
```

---

## 2.3 인증 전 Business Audit을 생성하지 않는다

요청 흐름은 다음과 같이 처리한다.

```text
Request
↓
Request Size / 기본 Envelope 검증
↓
Rate Limit
↓
Request ID / Trace ID
↓
Agent Credential 검증
```

인증 성공:

```text
Verified Agent Identity 생성
↓
Business AuditEvent 생성
↓
Authorization 진행
```

인증 실패:

```text
Business AuditEvent 생성 X
↓
최소 SecurityAuthEvent 기록
↓
요청 종료
```

인증 실패 기록 역시 Gateway가 DB에 직접 저장하지 않는다.

```text
Gateway
↓
Core Security Event API
↓
PostgreSQL
```

SecurityAuthEvent에는 Prompt, Document, 전체 Tool Argument, 고객 금융 데이터 등 민감한 원문을 저장하지 않는다.

---

## 2.4 Kubernetes 우회 방지는 이번 사이클에서 제외한다

Gateway 우회에 대한 Kubernetes NetworkPolicy 기반 차단은 P1 Hardening으로 미룬다.

이번 사이클에서는 다음만 구현한다.

```text
LoanAgent
→ Gateway
→ Authorization
→ ALLOW / BLOCK
→ Mock Finance
```

Kubernetes 관련 Manifest, NetworkPolicy, RBAC 구현은 다음 회의 전 필수 범위가 아니다.

---

# 3. Backend 1 — Financial Context / Permission / Core Persistence

## 담당 범위

- Employee Entity
- EmployeeAuthority Entity
- Consumer Entity
- ConsumerMandate Entity
- PermissionTemplate Entity
- FinancialCase Entity
- TaskPassport 계산 Skeleton
- Agent Effective Permission 계산 Skeleton
- Financial Context Resolver Skeleton
- ScopeStatus 계산 Skeleton
- JPA / PostgreSQL 기본 연동
- Runtime Context Internal API Skeleton
- Audit Persistence Skeleton
- SecurityAuthEvent Persistence Skeleton
- Behavior History 조회 Skeleton

## 핵심 권한 구조

```text
Employee Authority
∩ Permission Template
∩ Financial Case
∩ Consumer Mandate
        ↓
Agent Effective Permission
        ↓
Task Passport
```

## 역할 경계

Backend 1은 Financial Context와 FinGuard DB의 기준 저장소 역할을 담당한다.

```text
Gateway
→ DB 직접 접근 X

Gateway
→ Core Internal API
→ DB
```

Scope 비교 로직은 Context Resolver에서 단일 구현한다.

OPA나 Gateway에서 동일한 Scope 계산을 다시 구현하지 않는다.

## 이번 사이클 완료 기준

다음 시나리오를 DB / Context Resolver 수준에서 재현한다.

```text
EMP-101

Employee Authority
→ CUST-1001 조회 가능
→ CUST-9999 조회 가능

현재 Financial Case
→ CUST-1001

Agent 요청
→ CREDIT_SCORE_READ(CUST-9999)

결과
→ employeeAuthority = OK
→ customerScope = VIOLATION
```

Context Resolver 예시 출력:

```json
{
  "requestId": "REQ-001",
  "references": {
    "employeeId": "EMP-101",
    "caseId": "LOAN-2026-001",
    "passportId": "PASS-001"
  },
  "scopeStatus": {
    "employeeAuthority": "OK",
    "permissionTemplate": "OK",
    "caseStatus": "OK",
    "mandate": "OK",
    "passportStatus": "OK",
    "agentBinding": "OK",
    "customerScope": "VIOLATION",
    "toolScope": "OK",
    "dataScope": "OK"
  },
  "promptRiskSnapshot": {
    "evaluationStatus": "NOT_EVALUATED",
    "promptRisk": 0.00,
    "detected": false,
    "inputHash": "sha256:...",
    "modelVersion": "prompt-guard-4"
  }
}
```

즉,

> 직원 개인의 권한은 넓더라도 현재 Agent에게 발급된 업무 범위를 벗어나면 차단 가능한 Context를 만들어야 한다.

## 다음 회의에서 보여줄 것

- Entity / Seed 구조
- Effective Permission 계산 예시
- Task Passport 예시
- Context Resolver 입력 / 출력 JSON
- `customerScope = VIOLATION` 예시
- Core Internal API Skeleton
- Audit / Security Event 저장 Skeleton

---

# 4. Backend 2 — Gateway / OPA / Enforcement

## 담당 범위

- Gateway Skeleton — **Spring MVC + Java 21 Virtual Threads** (`docs/adr/0001` 참조. Spring Cloud Gateway를 쓰지 않는다)
- Tool Call Endpoint Skeleton
- Agent Credential 검증 Skeleton
- Verified Agent Identity Skeleton
- Request ID / Trace ID Skeleton
- Rate Limit / Request Size 제한 Skeleton
- OPA 실행 환경
- OPA Client Skeleton
- 기본 Rego Policy
- AuthorizationContext Skeleton
- `ALLOW / BLOCK` PolicyDecision Contract
- Fail-closed 기본 구조
- Core Context API Client Skeleton
- Core Audit API Client Skeleton
- Core Security Event API Client Skeleton
- AI Risk Engine Client Skeleton

## 역할 경계

Backend 2는 Employee Authority나 Case Scope를 직접 계산하지 않는다.

```text
Backend 1 Context Resolver
        ↓
ScopeStatus

AI Risk Engine
        ↓
AiRiskResult

        ↓
AuthorizationContext
        ↓
OPA
        ↓
PolicyDecision
        ↓
Gateway Enforcement
```

Gateway는 PostgreSQL에 직접 접근하지 않는다.

```text
Context 필요
→ Core API 호출

Audit 저장 필요
→ Core Audit API 호출

인증 실패 기록 필요
→ Core Security Event API 호출

Behavior History 필요
→ Core History API 호출
```

## 인증 흐름 Skeleton

```text
Tool Call Request
↓
Request Size / Envelope 검증
↓
Rate Limit
↓
Credential 검증

실패
→ Core Security Event API
→ SecurityAuthEvent 저장
→ 401 / 403 종료

성공
→ Verified Agent Identity 생성
→ Core Audit API
→ PROCESSING Business Audit 생성
→ Authorization 진행
```

## 이번 사이클 완료 기준

Mock Context를 사용하여 다음 시나리오를 실행한다.

```text
employeeAuthority = OK
customerScope = VIOLATION

        ↓

OPA

        ↓

decision = BLOCK
reasonCode = CASE_SCOPE_VIOLATION
```

Gateway는 `BLOCK`인 경우 Downstream Mock Finance를 호출하지 않는다.

## 다음 회의에서 보여줄 것

- Gateway 실행
- Credential 검증 Skeleton
- 인증 성공 / 실패 분기
- OPA 실행
- 기본 Rego Policy
- Mock `customerScope=VIOLATION` 요청
- OPA `BLOCK` 응답
- Reason Code 반환
- Core Internal API Mock Client
- Fail-closed 기본 구조

---

# 5. Backend 3 — Agent / Mock Finance / Audit Contract

## 담당 범위

- AgentRun Skeleton
- LoanAgent 또는 Agent Simulator Skeleton
- Mock Financial API
- Mock Consumer / Loan 데이터
- Internal Credential Skeleton
- AuditEvent Schema
- SecurityAuthEvent Schema 설계 지원
- Agent Action Log Schema
- ToolCallAttempt Schema
- ExecutionOutcome Schema
- 정상 / 공격 Scenario 초안
- Docker Compose 통합 지원

## 역할 경계

Backend 3는 Audit Schema와 Agent Runtime Event Contract를 설계한다.

DB Persistence는 Core를 통해 처리한다.

```text
Backend 3
→ AuditEvent / ToolCallAttempt / ExecutionOutcome Schema

Backend 1
→ Persistence / Internal API

Backend 2
→ Runtime에서 해당 API 호출
```

## 공격 Scenario 예시

### 정상

```text
CREDIT_SCORE_READ(CUST-1001)
→ Gateway
→ ALLOW
→ Mock Finance
```

### Case 우회 시도

```text
현재 Case = CUST-1001

Agent 요청
→ CREDIT_SCORE_READ(CUST-9999)
```

### Gateway 우회 시도

Gateway 우회에 대한 Kubernetes NetworkPolicy 구현은 이번 사이클 범위에서 제외한다.

현재는 정상적인 시스템 호출 흐름과 Mock Finance 독립 실행만 준비한다.

## Synthetic Data 역할

Backend 3는 다음을 담당한다.

- 실제 Runtime Log Schema 정의 지원
- 정상 / 이상 Agent 행동 Scenario 정의
- Agent 실행을 통한 Log 생성
- ToolCallAttempt / ExecutionOutcome Contract 제공

Synthetic Dataset Generator의 주 구현은 AI 담당이 맡는다.

## 이번 사이클 완료 기준

```text
LoanAgent / Simulator
        ↓
Gateway 요청 가능

Mock Finance
        ↓
독립 실행 가능

Agent Action / Audit
        ↓
JSON Schema 확정
```

## 다음 회의에서 보여줄 것

- LoanAgent / Simulator 실행
- Mock Finance API
- Mock 데이터
- Agent Action Log JSON
- ToolCallAttempt JSON
- ExecutionOutcome JSON
- 정상 / 공격 Scenario 예시

---

# 6. Frontend & AI

이번 사이클에서는 **완성 기능이 아니라 Mock UI와 AI 실험 환경 구축**까지만 진행한다.

---

## 6.1 Frontend

### 담당 범위

- Vue 프로젝트 Skeleton
- LoanAgent 실행 Mock UI
- Authority vs Effective Permission 화면
- Security Dashboard Mock
- Risk / Scope / Decision 표시 구조
- API Contract 기반 Mock Data 연결

### P0 핵심 화면

```text
1. LoanAgent 실행 / Financial Case

2. Employee Authority
   vs
   Agent Effective Permission

3. Security Dashboard
```

### 이번 사이클 완료 기준

실제 Backend API 연동 전이라도 Mock Data로 화면 흐름을 확인할 수 있어야 한다.

### 다음 회의에서 보여줄 것

- Vue 프로젝트 실행
- 3개 핵심 화면 Mock
- ALLOW / BLOCK 표시
- Scope / Risk / Reason Code 표시 영역

---

## 6.2 AI Risk Engine

### 담당 범위

- FastAPI Risk Engine Skeleton
- Behavior Feature Schema 초안
- Synthetic Dataset 형식 초안
- Isolation Forest 실험 환경
- 정상 / 이상 데이터 샘플
- 학습 / 추론 코드 기본 분리
- Prompt Detector Interface Skeleton 선택 사항

## Behavior Feature 후보

초기 후보 예시:

```text
requestCount1m
requestCount5m
uniqueCustomers5m
uniqueTools5m
caseSwitchCount5m
averageRequestIntervalMs
blockRatio5m
errorRatio5m
financialDataRequestCount5m
afterHoursAccess
```

Feature는 Backend 3의 ToolCallAttempt / ExecutionOutcome Contract와 Backend 1의 Behavior History 조회 Contract를 맞춘 뒤 확정한다.

## Behavior Risk 입력 흐름

Gateway는 DB에서 직접 과거 행동 이력을 조회하지 않는다.

```text
Gateway
↓
Core Behavior History API
↓
최근 Audit / Action History
↓
Gateway가 Current ToolCallAttempt 결합
↓
AI Risk Engine
↓
Feature Builder
↓
Isolation Forest
```

현재 요청의 미래 결과는 Feature에 포함하지 않는다.

```text
현재 Attempt에서 사용 가능
→ 요청 시점
→ Tool
→ 대상 고객
→ 요청 Context

현재 Attempt에서 사용 불가
→ success
→ latency
→ recordsRead
→ downstream outcome
```

후자의 값은 이전 완료된 ExecutionOutcome에서만 사용한다.

## Isolation Forest 이번 범위

이번 사이클에서는 모델 성능 최적화가 목표가 아니다.

최소한 다음 흐름이 실행 가능하면 된다.

```text
Synthetic Dataset
        ↓
Feature Builder
        ↓
IsolationForest.fit()
        ↓
Inference
        ↓
Raw Anomaly Score
```

Raw Anomaly Score를 확률값이라고 표현하지 않는다.

Risk Calibration과 Threshold 최적화는 이후 단계에서 진행한다.

## 이번 사이클 완료 기준

- FastAPI 실행 가능
- Feature Schema 초안 작성
- 정상 / 이상 Synthetic Data 샘플 준비
- Isolation Forest Train / Inference 실행 가능
- Behavior Risk API Mock 응답 가능

## 다음 회의에서 보여줄 것

- FastAPI Risk Engine 실행
- Behavior Feature JSON
- Synthetic Dataset 샘플
- Isolation Forest 실험 결과 예시
- Behavior Risk API 응답 예시

---

# 7. 이번 사이클에서 Prompt Injection은 어디까지 할 것인가

Prompt Injection 전체 구현은 이번 사이클의 필수 완료 범위로 두지 않는다.

우선순위는 다음과 같다.

```text
Financial Context / Scope
        ↓
Gateway + OPA
        ↓
Agent + Mock Finance
        ↓
Behavior AI Skeleton
        ↓
Prompt Injection
```

AI 담당자는 필요하면 Prompt Detector Interface 정도만 미리 만들어둘 수 있다.

예:

```text
POST /internal/v1/risk/prompt
```

단, 호출 기준은 다음과 같이 고정한다.

```text
새 Prompt / Document / 외부 입력 유입
→ Prompt Injection 검사

동일 입력
→ 기존 Prompt Risk 재사용

매 Tool Call
→ Prompt Detector 재호출하지 않음
```

실제 Rule / Model / 평가 Dataset 구현은 이후 Phase에서 진행한다.

## 다만 계약상 필드는 이번 사이클에도 흘려야 한다

구현을 미루는 것과 필드를 비우는 것은 다르다. `.rego`의 `valid_input`은
`input.risk.promptInjectionDetected`가 `true` 또는 `false`여야 참이 된다.

```rego
valid_input if {
    ...
    input.risk.promptInjectionDetected in {true, false}
}
deny_reasons contains "CONTEXT_NOT_FOUND" if { not valid_input }
```

**이 필드가 없으면 정상 요청까지 전량 BLOCK 되고**, §4 완료 기준의 데모
(`customerScope = VIOLATION → CASE_SCOPE_VIOLATION`)조차 엉뚱한 Reason Code로 실패한다.

그래서 이번 사이클 방침은 다음과 같다.

```text
Core Context Resolver 응답의 promptRiskSnapshot   반드시 포함
  evaluationStatus                                NOT_EVALUATED   ← 필수
  detected                                        false
  promptRisk                                      0.00
  inputHash / modelVersion                        형식만 채운다

Prompt Detector 실제 호출                          하지 않는다
```

`evaluationStatus`가 없으면 안 된다. `detected: false`만 저장하면 **검사를 수행한 결과
안전했다는 뜻이 되어 Audit 기록이 거짓이 된다.** 이 프로젝트는 Audit이 산출물이므로
"검사하지 않았음"과 "검사했고 음성"은 반드시 구분돼야 한다.

같은 이유로 이번 사이클에는 **Prompt Injection 방어를 성과로 주장하지 않는다.**
대시보드에도 `NOT_EVALUATED`가 그대로 드러나야 한다.

P1에서 실제 Detector를 붙일 때 `NOT_EVALUATED`는 **fail-closed로 전환한다** —
`false`로 번역하지 않는다.

정책을 optional 허용으로 완화하는 방식은 **선택하지 않는다.** 그건 `valid_input`에
fail-open 구멍을 다시 내는 것이고, 이 프로젝트의 전제를 무너뜨린다.

---

# 8. 담당자 간 Interface Freeze

다음 회의 전까지 각 담당자는 자신의 코드뿐 아니라 **다른 담당자가 Mock으로 사용할 수 있는 Contract**를 제공한다.

공통 Contract는 **`/docs/04-api-contract.md`를 Single Source of Truth로 사용한다.**
아래 §8의 예시와 어긋나는 곳이 있으면 `docs/04`가 이긴다.
OpenAPI Freeze 이후에 `/docs/api-contract.yaml`을 추가하고 그때 SSOT를 옮긴다(`docs/05:155` · `docs/README:47`과 동일).

공통 DTO / Enum / Reason Code는 3명이 합의하되, 실제 파일 수정 담당자는 한 명만 지정하고 나머지는 Review한다.

---

## 8.1 Backend 1 → Backend 2

### Runtime Financial Context Resolver

```text
POST /internal/v1/context/resolve
```

요청 예시:

```json
{
  "requestId": "550e8400-e29b-41d4-a716-446655440000",
  "verifiedAgentId": "LOAN-AGENT-01",
  "agentRunId": "RUN-001",
  "passportId": "PASS-001",
  "targetConsumerId": "CUST-9999",
  "requestedTool": "CREDIT_SCORE_READ",
  "requestedData": ["CREDIT_SCORE"]
}
```

응답 예시:

```json
{
  "requestId": "550e8400-e29b-41d4-a716-446655440000",
  "references": {
    "employeeId": "EMP-101",
    "caseId": "LOAN-2026-001",
    "passportId": "PASS-001"
  },
  "scopeStatus": {
    "employeeAuthority": "OK",
    "permissionTemplate": "OK",
    "caseStatus": "OK",
    "mandate": "OK",
    "passportStatus": "OK",
    "agentBinding": "OK",
    "customerScope": "VIOLATION",
    "toolScope": "OK",
    "dataScope": "OK"
  },
  "promptRiskSnapshot": {
    "evaluationStatus": "NOT_EVALUATED",
    "promptRisk": 0.00,
    "detected": false,
    "inputHash": "sha256:...",
    "modelVersion": "prompt-guard-4"
  }
}
```

**9개 Scope 값은 `scopeStatus` 객체 안에 넣는다.** 평면으로 올리면 Gateway가 OPA 입력을
다시 조립해야 하고, `.rego`의 `valid_input`이 거짓이 되어 정상 요청까지 BLOCK 된다.

`promptRiskSnapshot`은 이번 사이클에도 **반드시 포함한다.** §7에서 Prompt Injection 구현을
미뤘더라도 `detected`는 고정값 `false`로 채운다 — 이유는 §7 참조.

---

## 8.2 Backend 2 → Backend 3

### Gateway Tool Call

```text
POST /gateway/v1/tool-calls
```

Header 예시:

```text
Authorization: Bearer <agent-service-credential>
X-Request-Id: <UUID>
Traceparent: <W3C Trace Context>
```

요청 예시:

```json
{
  "agentRunId": "RUN-001",
  "passportId": "TP-001",
  "tool": "CREDIT_SCORE_READ",
  "targetConsumerId": "CUST-1001",
  "requestedData": ["CREDIT_SCORE"],
  "action": "READ"
}
```

다음 값은 Agent 요청 Body의 권한 근거로 신뢰하지 않는다.

```text
employeeId
agentId
caseId
purpose
allowedTools
allowedData
allowedActions
```

---

## 8.3 Backend 2 → Backend 1

### Business Audit 생성

```text
POST /internal/v1/audits
```

예시:

```json
{
  "requestId": "REQ-001",
  "agentRunId": "RUN-001",
  "verifiedAgentId": "LOAN-AGENT-01",
  "status": "PROCESSING",
  "requestedAt": "2026-08-17T20:00:00+09:00"
}
```

### Audit Outcome 갱신

```text
PATCH /internal/v1/audits/{requestId}/outcome
```

예시:

```json
{
  "decision": "BLOCK",
  "reasonCodes": ["CASE_SCOPE_VIOLATION"],
  "downstreamReached": false,
  "completedAt": "2026-08-17T20:00:01+09:00"
}
```

Business Audit 선저장이 실패하면 Downstream 금융 호출을 수행하지 않는다.

---

## 8.4 Backend 2 → Backend 1

### 인증 실패 Security Event

```text
POST /internal/v1/security-events/auth-failure
```

예시:

```json
{
  "requestId": "REQ-001",
  "eventType": "AUTH_FAILURE",
  "reasonCode": "INVALID_AGENT_CREDENTIAL",
  "credentialType": "AGENT_SERVICE",
  "occurredAt": "2026-08-17T20:00:00+09:00"
}
```

인증 실패 Event에는 Prompt / Document / 금융 데이터 원문을 저장하지 않는다.

---

## 8.5 Backend 2 → Backend 1

### Behavior History 조회

```text
GET /internal/v1/agents/{agentId}/behavior-history
```

예시 Query:

```text
window=5m
```

응답 예시:

```json
{
  "agentId": "LOAN-AGENT-01",
  "window": "5m",
  "completedEvents": [
    {
      "tool": "CREDIT_SCORE_READ",
      "targetConsumerId": "CUST-1001",
      "requestedAt": "2026-08-17T19:58:10+09:00",
      "decision": "ALLOW",
      "success": true,
      "latencyMs": 120
    }
  ]
}
```

Gateway는 이 History에 현재 ToolCallAttempt를 추가하여 AI Risk Engine으로 전달한다.

---

## 8.6 Backend 2 → AI

### Behavior Risk

```text
POST /internal/v1/risk/behavior
```

요청 예시:

```json
{
  "requestId": "REQ-001",
  "agentId": "LOAN-AGENT-01",
  "agentRunId": "RUN-001",
  "history": [],
  "currentAttempt": {
    "caseId": "LOAN-2026-001",
    "targetConsumerId": "CUST-1001",
    "tool": "CREDIT_SCORE_READ",
    "requestedData": ["CREDIT_SCORE"],
    "requestedAt": "2026-08-17T20:00:00+09:00"
  }
}
```

응답 예시:

```json
{
  "behaviorRisk": 0.82,
  "behaviorRiskLevel": "ALERT",
  "isAnomaly": true,
  "rawScore": -0.14,
  "historyStatus": "READY",
  "featureVersion": "behavior-features-1",
  "modelVersion": "iforest-1"
}
```

필드명은 `riskLevel`이 아니라 **`behaviorRiskLevel`**이다. `.rego`가 이 이름을 본다.

### `hardRequestLimitExceeded`의 생산자는 Gateway다

`.rego`가 요구하는 입력 3종 중 이것만 생산자가 없었다. **Gateway가 자체 카운터로 판정한다.**

AI가 아닌 이유는 두 가지다.

```text
requestCount1m 은 Tool Call **Attempt** 수다        docs/03 §8
Core behavior-history 는 completedEvents 만 준다     docs/04
  → AI는 Attempt를 셀 수 없다. 진행 중이거나 완료되지 못한 시도가 빠진다

Rate Limit 은 Credential 검증 **이전** 단계다        docs/02:134 · docs/04:210
  → 인증·History·AI 호출 전에 제한돼야 하는 경로가 있다. AI가 producer면 그 경로가 성립하지 않는다
```

Gateway는 모든 Attempt를 보는 유일한 지점이고, `docs/02` §7.2가 이미 Rate Limit을 Gateway 책임으로 둔다.
AI는 `requestCount1m`을 **관측 Feature로만** 쓴다 — 집행 카운터와 분리한다.

**아직 정해지지 않은 것 — 회의 안건:**

```text
§2.3의 인증 전 Rate Limit 과 hardRequestLimit1m 이 같은 규칙인가?

같다면   카운터 하나, 임계값 하나. 인증 전에 걸리면 OPA까지 가지 않는다
다르다면 두 한도의 키·윈도·순서·상호작용을 명시해야 한다
```

지금 문서는 이 둘을 구분하지 않는다. 정하지 않으면 Backend 2가 임의로 고른다.

---

## 8.7 Backend 2 → OPA

### AuthorizationContext

예시:

```json
{
  "input": {
    "requestId": "REQ-001",
    "scopeStatus": {
      "employeeAuthority": "OK",
      "permissionTemplate": "OK",
      "caseStatus": "OK",
      "mandate": "OK",
      "passportStatus": "OK",
      "agentBinding": "OK",
      "customerScope": "VIOLATION",
      "toolScope": "OK",
      "dataScope": "OK"
    },
    "risk": {
      "promptRisk": 0.05,
      "promptInjectionDetected": false,
      "behaviorRisk": 0.21,
      "behaviorRiskLevel": "LOW",
      "behaviorAnomalyDetected": false
    },
    "limits": {
      "hardRequestLimitExceeded": false
    }
  }
}
```

OPA 응답 예시:

```json
{
  "result": {
    "decision": "BLOCK",
    "severity": "HIGH",
    "riskFlagged": true,
    "reasonCodes": ["CASE_SCOPE_VIOLATION"],
    "policyVersion": "loan-review-policy-1"
  }
}
```

세 가지를 특히 주의한다.

```text
OPA 요청은 { "input": ... } 로 감싼다        OPA Data API 규약
risk / limits 는 중첩 객체다                 평면으로 올리면 valid_input 거짓 → 전량 BLOCK
policyVersion 은 loan-review-policy-1        "v1" 이 아니다 (policy/finguard_authz.rego:5)
```

`.rego`가 실제로 읽는 필드는 **점수가 아니라 판정값**이다.

```text
input.risk.promptInjectionDetected      boolean          ← promptRisk(실수)가 아니다
input.risk.behaviorRiskLevel            LOW|ALERT|CRITICAL ← behaviorRisk(실수)가 아니다
input.limits.hardRequestLimitExceeded   boolean
```

`promptRisk` · `behaviorRisk` 실수값도 함께 보내지만 현재 정책은 참조하지 않는다. Audit 기록용이다.

### Gateway가 수행하는 필드 매핑

이름이 그대로 넘어가지 않는다. 지금까지 어느 문서에도 적혀 있지 않아 명시한다.

| 출처 | 필드 | → AuthorizationContext |
|---|---|---|
| Core `context/resolve` | `scopeStatus` | `input.scopeStatus` (그대로) |
| Core `context/resolve` | `promptRiskSnapshot.detected` | `input.risk.promptInjectionDetected` |
| Core `context/resolve` | `promptRiskSnapshot.promptRisk` | `input.risk.promptRisk` |
| AI `risk/behavior` | `behaviorRiskLevel` | `input.risk.behaviorRiskLevel` (그대로) |
| AI `risk/behavior` | `behaviorRisk` | `input.risk.behaviorRisk` (그대로) |
| AI `risk/behavior` | **`isAnomaly`** | **`input.risk.behaviorAnomalyDetected`** |
| Gateway 자체 카운터 | 한도 초과 여부 | `input.limits.hardRequestLimitExceeded` |

`detected → promptInjectionDetected`와 `isAnomaly → behaviorAnomalyDetected` 두 개가 이름이 바뀐다.
매핑을 빠뜨리면 `valid_input`이 거짓이 되어 전량 BLOCK 된다.

---

## 8.8 Backend 3 → AI

### ToolCallAttempt / ExecutionOutcome

ToolCallAttempt 예시:

```json
{
  "requestId": "REQ-001",
  "agentId": "LOAN-AGENT-01",
  "agentRunId": "RUN-001",
  "tool": "CREDIT_SCORE_READ",
  "targetConsumerId": "CUST-1001",
  "requestedAt": "2026-08-17T20:00:00+09:00"
}
```

ExecutionOutcome 예시:

```json
{
  "requestId": "REQ-001",
  "decision": "ALLOW",
  "downstreamReached": true,
  "success": true,
  "recordsRead": 1,
  "latencyMs": 120,
  "completedAt": "2026-08-17T20:00:00+09:00"
}
```

AI 담당은 이 Contract를 기반으로 Synthetic Behavior Dataset을 생성한다.

---

# 9. Scenario Ownership

코드 Ownership은 기존 모듈 기준으로 유지하고, E2E 완성 책임만 Scenario Owner로 추가한다.

```text
Backend 1
→ Core / DB / Context / Scope Owner

Backend 2
→ Gateway / Authorization / OPA / Policy Owner

Backend 3
→ Agent / Mock Finance / Audit Contract Owner
```

Scenario Owner 예시:

```text
Backend 1
→ 정상 ALLOW + Identity / Passport E2E

Backend 2
→ Case BLOCK + Idempotency E2E

Backend 3
→ Tool / Data / Mandate + Fail-closed E2E
```

원칙:

> Scenario Owner는 해당 E2E 시나리오의 완성을 책임지지만, 다른 모듈의 핵심 로직을 독단적으로 구현하지 않는다. 필요한 변경은 해당 Module Owner와 Contract를 통해 조율한다.

---

# 10. 다음 회의 체크리스트

## Backend 1

- [ ] Entity / Seed 구조 준비
- [ ] PermissionTemplate 포함
- [ ] Effective Permission 계산 Skeleton
- [ ] Task Passport Skeleton
- [ ] Context Resolver 실행
- [ ] `customerScope=VIOLATION` 예시
- [ ] Core Runtime Context API Skeleton
- [ ] Audit Persistence Skeleton
- [ ] SecurityAuthEvent Persistence Skeleton
- [ ] Behavior History 조회 Skeleton

## Backend 2

- [ ] Gateway 실행
- [ ] Request Size / Rate Limit Skeleton
- [ ] Agent Credential 검증 Skeleton
- [ ] 인증 성공 / 실패 분기
- [ ] Verified Agent Identity 생성
- [ ] OPA 실행
- [ ] 기본 Rego Policy
- [ ] OPA Client Skeleton
- [ ] Mock ScopeStatus 기반 BLOCK
- [ ] Reason Code 반환
- [ ] Core Context / Audit / Security API Client Skeleton
- [ ] AI Risk Client Skeleton
- [ ] Fail-closed Skeleton

## Backend 3

- [ ] AgentRun Skeleton
- [ ] LoanAgent / Simulator 실행
- [ ] Mock Finance API 실행
- [ ] Mock Consumer / Loan 데이터
- [ ] AuditEvent Schema
- [ ] ToolCallAttempt Schema
- [ ] ExecutionOutcome Schema
- [ ] 정상 / 공격 Scenario

## Frontend

- [ ] Vue 실행
- [ ] LoanAgent 실행 화면
- [ ] Authority 비교 화면
- [ ] Security Dashboard Mock
- [ ] Scope / Risk / Reason Code 표시

## AI

- [ ] FastAPI 실행
- [ ] Behavior Feature Schema
- [ ] Synthetic Dataset 형식
- [ ] Isolation Forest 학습 실행
- [ ] Isolation Forest 추론 실행
- [ ] Behavior Risk Mock API
- [ ] Prompt Detector Interface 선택 사항

---

# 11. 다음 회의의 목표

다음 회의에서는 각 담당자의 구현량보다 **컴포넌트 간 연결 가능 여부**를 우선 확인한다.

최소한 다음 흐름이 Mock으로라도 연결 가능해야 한다.

```text
                         PostgreSQL
                             ↑
                             │
                       Backend 1 Core
                      ↗              ↖
          Context / History          Audit / Security
                 ↑                       ↑
                 └──── Backend 2 Gateway ┘
                           │
                    ┌──────┴──────┐
                    │             │
                    ▼             ▼
                  OPA          AI Risk
                    │
                    ▼
              Backend 3 Agent
                    │
                    ▼
             Mock Financial API

Frontend
→ Core / Gateway Contract 기반 Mock 또는 연동
```

최종 목표:

> 각자 독립적으로 개발하되, 다음 단계에서 Contract 변경 없이 연결할 수 있는 상태를 만든다.

---

# 12. 이번 사이클에서 명시적으로 하지 않는 것

다음 항목은 이번 회의 전 필수 구현 범위가 아니다.

```text
Kubernetes
NetworkPolicy
ServiceAccount / RBAC
Prompt Injection 모델 비교평가
Prompt Injection 금융 평가 Dataset 완성
Behavior Risk Calibration 최적화
실제 금융 API
OpenSearch
Human Approval
MASK
```

이번 사이클의 우선순위는 다음과 같다.

```text
Contract
→ Core Context
→ Gateway / OPA
→ Agent / Mock Finance
→ Audit Contract
→ Behavior AI Skeleton
→ Frontend Mock
→ 다음 단계 통합
```
