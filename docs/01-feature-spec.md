# FinGuard MVP 기능 명세

## 1. 기능 목록

| ID | 기능 | 주 담당 | 우선순위 |
|---|---|---|---|
| F01 | Employee Authority | Backend 1 | P0 |
| F02 | Consumer Mandate Seed | Backend 1 | P0 |
| F03 | Permission Template | Backend 1 | P0 |
| F04 | Financial Case | Backend 1 | P0 |
| F05 | Agent Effective Permission / Task Passport | Backend 1 | P0 |
| F06 | AgentRun | Backend 3 | P0 |
| F07 | LoanAgent Tool Call | Backend 3 | P0 |
| F08 | Runtime Tool Call Interception | Backend 2 | P0 |
| F09 | Verified Agent Identity | Backend 2 | P0 |
| F10 | Authentication / Audit Start | Backend 2 + Backend 1 | P0 |
| F11 | Financial Context Resolver / Scope Status | Backend 1 | P0 |
| F12 | Prompt Injection Detection | Frontend & AI | P0 |
| F13 | Behavior Feature Builder | Frontend & AI | P0 |
| F14 | Isolation Forest Detection | Frontend & AI | P0 |
| F15 | AuthorizationContext 생성 | Backend 2 | P0 |
| F16 | OPA PolicyDecision | Backend 2 | P0 |
| F17 | Runtime Enforcement | Backend 2 | P0 |
| F18 | Mock Financial API | Backend 3 | P0 |
| F19 | Execution Outcome / Audit Persistence | Backend 1 + Backend 3 Contract | P0 |
| F20 | Web UI / Security Dashboard | Frontend & AI | P0 |
| F21 | Docker Compose 실행환경 | Backend 3 중심, 전원 | P0 |

### P1 확장

- Consumer Mandate CRUD / 철회
- PII / Sensitive Data Response Inspection
- `MASK / APPROVAL`
- Human Approval UI
- Kubernetes NetworkPolicy / RBAC / ServiceAccount
- OpenSearch / Risk History

---

## F01. Employee Authority

### 목적

직원이 원래 수행할 수 있는 금융업무 권한을 서버에서 관리하고, Agent 권한의 최대 상한선으로 사용한다.

### 예시 Schema

```json
{
  "employeeId": "EMP-101",
  "status": "ACTIVE",
  "allowedCustomerScope": "ALL",
  "allowedTools": [
    "CREDIT_SCORE_READ",
    "INCOME_READ",
    "DEBT_READ"
  ],
  "allowedData": [
    "CREDIT_SCORE",
    "INCOME",
    "DEBT"
  ],
  "version": 1
}
```

### 처리 규칙

- Agent가 보낸 `employeeId`나 권한 목록을 Runtime 권한의 근거로 신뢰하지 않는다.
- Employee Authority는 서버 DB의 신뢰 가능한 값만 사용한다.
- Agent Effective Permission은 Employee Authority를 초과할 수 없다.
- 직원 권한이 비활성인 경우 AgentRun을 시작하지 않는다.

### 완료 조건

- EMP-101의 권한을 서버에서 조회할 수 있다.
- EMP-101이 여러 고객을 조회할 권한이 있어도 현재 Agent Case는 더 좁게 제한될 수 있다.

---

## F02. Consumer Mandate Seed

### 목적

대출심사 목적에서 해당 Consumer가 허용한 Data 범위를 Agent 권한 계산에 반영한다.

P0에서는 CRUD UI를 구현하지 않고 Seed Data로 제공한다.

### 예시 Schema

```json
{
  "consumerId": "CUST-1001",
  "purpose": "LOAN_REVIEW",
  "allowedData": [
    "CREDIT_SCORE",
    "INCOME",
    "DEBT"
  ],
  "status": "ACTIVE",
  "version": 1
}
```

### 처리 규칙

- Consumer Mandate는 현재 Case의 `consumerId + purpose`와 일치해야 한다.
- Mandate 밖 Data는 `MANDATE_SCOPE_VIOLATION`으로 처리한다.
- P0에서는 Seed Data를 읽기 전용으로 사용한다.
- 수정·철회 UI는 P1이다.

---

## F03. Permission Template

### 목적

`LOAN_REVIEW` 업무에 필요한 표준 Tool/Data 범위를 서버에서 관리한다.

### 예시

```json
{
  "templateId": "LOAN_REVIEW_STANDARD",
  "taskType": "LOAN_REVIEW",
  "allowedTools": [
    "CREDIT_SCORE_READ",
    "INCOME_READ",
    "DEBT_READ"
  ],
  "allowedData": [
    "CREDIT_SCORE",
    "INCOME",
    "DEBT"
  ],
  "defaultDurationMinutes": 60,
  "status": "ACTIVE",
  "version": 1
}
```

### 처리 규칙

- Agent가 제출한 Tool/Data 목록으로 Template을 변경하지 않는다.
- Permission Template은 Employee Authority를 대체하지 않는다.
- Permission Template은 업무별 표준 제약조건으로 Employee Authority와 교집합을 계산한다.

---

## F04. Financial Case

### 목적

현재 Agent가 어떤 고객의 어떤 금융업무를 수행하는지 명시한다.

### Schema

```json
{
  "caseId": "LOAN-2026-001",
  "employeeId": "EMP-101",
  "consumerId": "CUST-1001",
  "taskType": "LOAN_REVIEW",
  "templateId": "LOAN_REVIEW_STANDARD",
  "status": "ACTIVE",
  "issuedAt": "2026-08-17T14:00:00+09:00",
  "expiresAt": "2026-08-17T15:00:00+09:00",
  "version": 1
}
```

### 처리 규칙

- Case의 Consumer가 Runtime Tool Call의 고객 Scope 기준이 된다.
- Case가 비활성·만료되면 Tool Call을 차단한다.
- Agent가 Runtime 요청 Body에서 Case 내용을 재정의할 수 없다.

---

## F05. Agent Effective Permission / Task Passport

### 목적

현재 Case에서 Agent에게 실제로 유효한 최소권한을 계산하고 Runtime Snapshot으로 발급한다.

### 권한 계산

```text
Employee Authority
∩ Permission Template
∩ Financial Case
∩ Consumer Mandate
        ↓
Agent Effective Permission
```

### Task Passport 예시

```json
{
  "passportId": "PASS-001",
  "agentId": "LOAN-AGENT-01",
  "employeeId": "EMP-101",
  "caseId": "LOAN-2026-001",
  "consumerId": "CUST-1001",
  "taskType": "LOAN_REVIEW",
  "allowedTools": [
    "CREDIT_SCORE_READ",
    "INCOME_READ",
    "DEBT_READ"
  ],
  "allowedData": [
    "CREDIT_SCORE",
    "INCOME",
    "DEBT"
  ],
  "status": "ACTIVE",
  "issuedAt": "2026-08-17T14:00:00+09:00",
  "expiresAt": "2026-08-17T15:00:00+09:00",
  "sourceVersions": {
    "employeeAuthority": 1,
    "permissionTemplate": 1,
    "financialCase": 1,
    "consumerMandate": 1
  }
}
```

### 처리 규칙

- Passport는 서버에 저장한다.
- Agent는 권한 내용을 직접 수정하지 않고 `passportId`만 Runtime 요청에 사용한다.
- Passport Agent와 Verified Agent가 일치해야 한다.
- 만료·비활성 Passport는 차단한다.
- Source Version이 변경된 Passport는 `TASK_PASSPORT_STALE`로 처리할 수 있다.

### 완료 조건

- `Agent Effective Permission ⊆ Employee Authority`가 항상 성립한다.
- EMP-101이 CUST-9999 조회 권한을 가져도 CUST-1001 Case의 Passport는 CUST-1001만 허용한다.

---

## F06. AgentRun

### 목적

LoanAgent의 한 번의 대출심사 실행을 Case·Passport·입력과 연결한다.

### API

```http
POST /api/v1/agent-runs
```

### 입력 예시

```json
{
  "employeeId": "EMP-101",
  "consumerId": "CUST-1001",
  "taskType": "LOAN_REVIEW",
  "inputText": "CUST-1001의 대출심사를 진행해줘."
}
```

### 처리 순서

```text
Employee Authority 조회
→ Permission Template 조회
→ Consumer Mandate 조회
→ Financial Case 생성
→ Effective Permission 계산
→ Task Passport 발급
→ AgentRun RUNNING
→ 생성 트랜잭션 커밋 후 Core가 Agent Simulator 호출
→ Agent가 발급된 agentRunId / passportId로 Gateway 호출
```

### 보안 규칙

- AgentRun 입력 원문은 통제된 저장소에 보관한다.
- AgentRun/Case/Passport/Input Reference의 발급·저장은 Core 책임이다.
- P0 Agent는 Core가 호출하는 결정론적 Simulator이며 Core 생성 API를 호출하지 않는다.
- Audit에는 원문 대신 `inputRef / inputHash`만 저장한다.
- Tool Call 시 Agent가 Prompt를 다시 첨부한 값을 신뢰하지 않는다.

---

## F07. LoanAgent Tool Call

### 목적

LoanAgent가 대출심사 중 필요한 금융 Tool을 Gateway를 통해 호출한다.

### Tool

```text
CREDIT_SCORE_READ
INCOME_READ
DEBT_READ
```

### 입력 예시

```json
{
  "agentRunId": "RUN-001",
  "passportId": "PASS-001",
  "targetConsumerId": "CUST-1001",
  "tool": "CREDIT_SCORE_READ",
  "requestedData": ["CREDIT_SCORE"]
}
```

### 처리 규칙

- LoanAgent는 Mock Financial API를 직접 호출하지 않는다.
- Runtime Body의 `agentId`, `employeeId`, 권한 목록은 Identity/Permission 근거로 사용하지 않는다.
- Service Credential을 Gateway에 전달한다.

---

## F08. Runtime Tool Call Interception

### 목적

LoanAgent의 금융 Tool Call을 실제 금융 API 도달 전에 가로챈다.

### 처리 흐름

```text
LoanAgent
→ Spring Cloud Gateway
→ Identity / Context / AI / OPA
→ ALLOW일 때만 Mock Financial API
```

### 완료 조건

- BLOCK 요청은 Mock Financial API 호출 횟수가 증가하지 않는다.
- Gateway를 거치지 않은 직접 호출은 P0 내부 Credential 검증으로 실패한다.

---

## F09. Verified Agent Identity

### 목적

Runtime Body의 Agent 주장값이 아니라 Gateway가 검증한 Credential로 Agent Identity를 결정한다.

### 출력

```json
{
  "verifiedAgentId": "LOAN-AGENT-01",
  "credentialStatus": "VERIFIED"
}
```

### 처리 규칙

- 외부에서 전달된 내부 Identity Header는 제거하거나 무시한다.
- Passport의 `agentId`와 Verified Agent ID가 다르면 `AGENT_IDENTITY_MISMATCH`로 차단한다.
- 인증 실패 시 금융 API를 호출하지 않는다.

---

## F10. Authentication / Audit Start

### 목적

인증되지 않은 요청이 Business Audit DB Write를 무제한 유발하지 않도록 **Rate Limit과 Agent 인증을 먼저 수행**하면서, 인증 실패 흔적도 별도의 Security Event로 추적한다.

### 처리 순서

```text
Request Size / 최소 Envelope
↓
Rate Limit
↓
Request ID / Trace ID
↓
Agent Credential 검증
```

인증 성공:

```text
Verified Agent Identity
↓
Gateway → Core Audit API
↓
PROCESSING Business AuditEvent 저장
↓
Authorization 진행
```

인증 실패:

```text
Gateway → Core Security Event API
↓
최소 SecurityAuthEvent 저장
↓
401 / 403 종료
```

### 처리 규칙

- Gateway는 PostgreSQL에 직접 접근하지 않는다.
- 인증 실패 요청에 대해 Business AuditEvent를 만들지 않는다.
- SecurityAuthEvent에는 Prompt/Document/금융 데이터 원문을 저장하지 않는다.
- Business Audit 선저장이 실패하면 Downstream 금융 호출을 수행하지 않는다.

## F11. Financial Context Resolver / Scope Status

### 목적

서버의 신뢰 가능한 Context로 각 권한 범위의 상태를 계산한다.

### 조회

```text
Employee Authority
Permission Template
Financial Case
Consumer Mandate
Task Passport
AgentRun
```

### 출력 예시

```json
{
  "employeeAuthority": "OK",
  "permissionTemplate": "OK",
  "caseStatus": "OK",
  "mandate": "OK",
  "passportStatus": "OK",
  "agentBinding": "OK",
  "customerScope": "VIOLATION",
  "toolScope": "OK",
  "dataScope": "OK"
}
```

### 책임 경계

- Context Resolver는 `OK / VIOLATION` 상태를 계산한다.
- Context Resolver는 최종 `ALLOW / BLOCK`을 결정하지 않는다.
- Scope 비교 로직은 Context Resolver의 Single Source of Truth다.
- OPA는 동일한 Case/Tool/Data 비교를 Rego에서 다시 구현하지 않는다.

### 예시

```text
Employee Authority
→ CUST-9999 조회 가능

현재 Financial Case
→ CUST-1001

요청
→ CUST-9999

employeeAuthority = OK
customerScope = VIOLATION
```

---

## F12. Prompt Injection Detection

### 목적

AgentRun의 Prompt, 참조 문서 등 **새로운 비신뢰 입력이 유입될 때** 악성 지시를 탐지하고 재사용 가능한 Prompt Risk Snapshot을 만든다.

### API

```http
POST /internal/v1/risk/prompt
```

### 호출 기준

```text
새 Prompt / Document / 외부 입력
→ 검사

동일 inputHash + modelVersion
→ 재검사하지 않음
→ 기존 Prompt Risk Snapshot 재사용

Tool Call 발생
→ 동일 입력에 대한 Prompt Detector 반복 호출 X
```

### 출력 예시

```json
{
  "detected": true,
  "promptRisk": 0.96,
  "attackType": "CROSS_CUSTOMER_ACCESS",
  "matchedRules": ["IGNORE_PREVIOUS_INSTRUCTION"],
  "inputHash": "sha256:...",
  "modelVersion": "prompt-guard-5"
}
```

### 처리 규칙

- 한국어·영어 Rule Detection과 분류 모델을 결합한다.
- 원본 입력을 FastAPI Application Log / DB에 저장하지 않는다.
- 모델 오류를 낮은 Risk로 대체하지 않는다.
- Prompt Risk는 권한을 확대하지 않는다.
- Threshold는 평가 결과로 고정한다.

## F13. Behavior Feature Builder

### 목적

최근 Agent 행동을 Isolation Forest 입력 Feature로 변환한다.

### 주요 Feature

```text
requestCount1m
requestCount5m
uniqueCustomers5m
uniqueTools5m
blockRatio5m
errorRatio5m
averageRequestIntervalMs
caseSwitchCount5m
financialDataRequestCount5m
afterHoursAccess
```

### 처리 규칙

- `ToolCallAttempt`와 `ExecutionOutcome`을 분리한다.
- 현재 요청의 미래 `success / recordsRead` 등을 실행 전 Feature에 넣지 않는다.
- Gateway가 Core Behavior History API에서 받은 최근 완료 이력과 현재 Attempt를 입력으로 사용한다.
- Gateway는 Behavior History를 위해 PostgreSQL을 직접 조회하지 않는다.
- 학습과 Runtime이 동일한 Feature Builder 코드를 사용한다.
- 이력 부족 시 `COLD_START`를 반환한다.

---

## F14. Isolation Forest Detection

### 목적

Scope상 정상인 행동이라도 누적 패턴이 정상 행동 분포에서 크게 벗어나는지 탐지한다.

### 출력

```json
{
  "isAnomaly": true,
  "behaviorRisk": 0.97,
  "historyStatus": "READY",
  "featureVersion": "behavior-features-1",
  "modelVersion": "iforest-1"
}
```

### 정책 입력 의미

```text
behaviorRisk < alertThreshold
→ 정상 Risk

alertThreshold <= behaviorRisk < criticalThreshold
→ 위험 표시

behaviorRisk >= criticalThreshold
→ AI 단독 차단 가능
```

Hard Request Limit은 Isolation Forest와 별도의 deterministic Rule이다.

### 완료 조건

- Scope가 모두 `OK`이고 Hard Limit 미초과인 요청도 Critical Behavior Risk만으로 BLOCK되는 테스트가 존재한다.
- False Positive Rate를 반드시 기록한다.

---

## F15. AuthorizationContext 생성

### 목적

Scope Status와 AI Risk를 OPA Policy 입력으로 구성한다.

### 필수 정보

```text
requestId
traceId
verifiedAgentId
agentRunId
caseId
passportId
Scope Status
Prompt Risk
Behavior Risk
Hard Limit Status
```

### 처리 규칙

- Prompt 원문과 금융 API Payload 원문을 OPA에 전달하지 않는다.
- OPA에는 필요한 상태값과 Risk만 전달한다.
- 필수 Context가 없으면 OPA 호출 전 Fail-closed 처리한다.

---

## F16. OPA PolicyDecision

### 목적

AuthorizationContext를 Rego 정책으로 평가해 최종 `ALLOW / BLOCK`을 생성한다.

### 출력 예시

```json
{
  "decision": "BLOCK",
  "severity": "CRITICAL",
  "riskFlagged": true,
  "reasonCodes": [
    "CASE_SCOPE_VIOLATION"
  ],
  "policyVersion": "loan-review-policy-1"
}
```

### 결정 규칙

| 조건 | Decision | riskFlagged |
|---|---|---|
| 모든 Scope 정상 + Risk 낮음 | ALLOW | false |
| Employee Authority 위반 | BLOCK | true |
| Case/Mandate/Passport/Tool/Data 위반 | BLOCK | true |
| Agent Binding 위반 | BLOCK | true |
| Prompt Injection 차단 조건 | BLOCK | true |
| Behavior Alert 구간 | ALLOW | true |
| Behavior Critical 구간 | BLOCK | true |
| Hard Request Limit 초과 | BLOCK | true |
| 필수 서비스 오류 | BLOCK | true |

### 중요 규칙

OPA는 이미 계산된 Scope Status를 정책 입력으로 사용한다. 동일한 Scope 비교를 다시 구현하지 않는다.

---

## F17. Runtime Enforcement

### 목적

OPA Decision을 실제 금융 Tool 실행에 반영한다.

### ALLOW

```text
내부 요청 Credential 생성
→ Mock Financial API 호출
→ 응답 수신
→ LoanAgent에 Tool Result 반환
→ downstreamReached=true
→ responseReleased=true
```

### BLOCK

```text
Mock Financial API 미호출
→ 403 + Reason Code
→ downstreamReached=false
→ responseReleased=false
```

### 처리 규칙

- OPA 응답이 없거나 형식이 잘못되면 BLOCK한다.
- 동일 Request ID를 두 번 실행하지 않는다.
- 내부 Policy 구현 세부사항은 Agent에 노출하지 않는다.

---

## F18. Mock Financial API

### 목적

정상 요청의 실제 실행과 위험 요청의 미도달을 검증한다.

| Tool | 반환 정보 |
|---|---|
| `CREDIT_SCORE_READ` | 신용점수 |
| `INCOME_READ` | 연 소득 |
| `DEBT_READ` | 총 부채 |

### 보안 규칙

- 시연용 가상 데이터만 사용한다.
- 정상 P0 Runtime 경로는 `LoanAgent → Gateway → Mock Finance`로 고정한다.
- BLOCK 요청이 Mock Finance에 도달하지 않는지 호출 횟수로 검증할 수 있어야 한다.
- NetworkPolicy를 이용한 직접 우회 차단 보장은 P1 Kubernetes Hardening에서 검증한다.

---

## F19. Execution Outcome / Audit Persistence

### 목적

인증 성공 후 생성된 Business AuditEvent에 최종 실행 결과를 반영하고, 인증 실패 Event와 Business Audit을 명확히 분리한다.

### 역할

```text
Backend 3
→ AuditEvent / ToolCallAttempt / ExecutionOutcome Contract

Backend 1 Core
→ Audit / SecurityAuthEvent Persistence + Internal API

Backend 2 Gateway
→ Runtime에서 Core Audit API 호출
```

### Business Audit 저장 항목

```text
Verified Agent
Employee / AgentRun / Case / Passport
Target Consumer / Tool / Data Type
Scope Status
Prompt Risk Snapshot / Model Version
Behavior Risk / Feature·Model Version
OPA Decision / Policy Version
Downstream 도달 여부
Response 반환 여부
ERROR 위치
Reason Code
```

### 처리 규칙

- `ALLOW / BLOCK / ERROR`를 모두 저장한다.
- 인증 실패는 별도 `SecurityAuthEvent`로 저장한다.
- 원본 Prompt, 금융 문서, 금융 API Response Payload를 저장하지 않는다.
- Gateway는 Audit DB를 직접 수정하지 않고 Core Internal API만 호출한다.
- 외부에서 AuditEvent를 수정·삭제하는 API를 제공하지 않는다.

## F20. Web UI / Security Dashboard

P0 Web은 은행 직원의 실제 업무 흐름을 끊지 않도록 두 개의 주요 화면으로 구성한다.
Employee Authority와 Agent Effective Permission 비교는 별도 메뉴가 아니라 LoanAgent 실행
화면의 현재 업무 보호 패널에 통합한다.

### P0 화면 1 — AI 업무 지원 / LoanAgent 실행

- 직원은 현재 수행할 금융업무만 선택한다.
- Consumer, Financial Case와 Task Passport를 현재 업무 기준으로 확인한다.
- Employee가 원래 접근 가능한 범위와 이번 업무의 Agent Effective Permission을 함께 표시한다.
- Agent 허용 Tool/Data와 Task Passport 만료시간을 보안 상세에서 확인한다.
- AgentRun을 생성하고 Tool Call별 ALLOW/BLOCK/ERROR Outcome을 표시한다.
- 직원이 요청한 업무와 Agent가 실제로 시도한 Tool Call을 구분한다.
- 직원의 요청이나 화면 선택만으로 Agent 권한을 확대하지 않는다.

### P0 화면 2 — Security Dashboard

표시:

```text
발생 시간
Agent
Employee
Case
Target Consumer
Tool
Scope Status
Prompt Risk
Prompt Evaluation Status / Model Version
Behavior Risk
Behavior Risk Level / Feature Version / Model Version
PolicyDecision (ALLOW / BLOCK)
Audit / System Outcome (COMPLETED / ERROR)
Severity
Reason Code
Policy Version
Downstream 도달 여부
Response 제공 여부
```

필터:

```text
기간 / Agent / Case / Consumer / Tool / 처리 결과 / Severity / Reason Code
```

### 처리 규칙

- Vue는 PostgreSQL을 직접 조회하지 않는다.
- Spring의 Read-only Dashboard API만 호출한다.
- 원본 Prompt와 금융 API 원문을 표시하지 않는다.

---

## F21. Docker Compose 실행환경

### 목적

팀 개발과 심사 환경에서 FinGuard 전체 서비스를 재현 가능하게 실행한다.

### 대상

```text
Spring Backend
Spring Cloud Gateway
LoanAgent
Mock Financial API
FastAPI AI Risk Engine
OPA
PostgreSQL
Vue Frontend
```

### 완료 조건

- `docker compose up` 기준으로 필수 서비스가 실행된다.
- 정상/공격 E2E 시나리오를 재현할 수 있다.
- 환경변수/Secret은 저장소에 평문 Commit하지 않는다.

---

## 2. 공통 인수 조건

| ID | 조건 | 기대 결과 |
|---|---|---|
| AC-01 | 정상 Case·Passport·Tool·Data | ALLOW, Downstream 1회, Business Audit 저장 |
| AC-02 | Employee는 가능하지만 현재 Case 밖 고객 | `CASE_SCOPE_VIOLATION`, BLOCK, Downstream 0회 |
| AC-03 | 새로운 Prompt/Document에 Prompt Injection | 입력 유입 시 탐지, BLOCK 가능한 Prompt Risk Snapshot 생성 |
| AC-04 | 동일 Prompt/Document로 여러 Tool Call | Prompt Detector 재추론 없이 기존 Snapshot 재사용 |
| AC-05 | Prompt Detector가 놓친 Case 위반 | Case Rule로 BLOCK |
| AC-06 | Passport 밖 Tool/Data | BLOCK |
| AC-07 | Mandate 밖 Data | BLOCK |
| AC-08 | 만료 Passport | `TASK_PASSPORT_EXPIRED`, BLOCK |
| AC-09 | Agent Identity 불일치 | `AGENT_IDENTITY_MISMATCH`, BLOCK |
| AC-10 | 인증 실패 | Business Audit 생성 X, 최소 SecurityAuthEvent DB 저장 |
| AC-11 | Behavior Alert 구간 단독 | ALLOW + 위험 표시 |
| AC-12 | Scope 정상 + Hard Limit 미초과 + Behavior Critical | AI Risk로 BLOCK |
| AC-13 | Hard Request Limit 초과 | Rule 기반 BLOCK |
| AC-14 | Risk/OPA/Core 필수 의존성 오류 | Fail-closed BLOCK |
| AC-15 | Business Audit 선저장 실패 | Downstream 0회 |
| AC-16 | Gateway의 FinGuard DB 직접 접근 | 금지, Core Internal API 경유 |
| AC-17 | Mock Finance 오류 | ERROR Audit |
| AC-18 | 정상·차단·오류 | Dashboard에서 모두 조회 |
| AC-19 | Docker Compose | 전체 P0 E2E 재현 가능 |
| AC-P1-01 | LoanAgent의 Gateway 네트워크 우회 | Kubernetes NetworkPolicy 적용 시 차단 |
