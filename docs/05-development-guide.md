# FinGuard MVP 개발 가이드

## 1. 개발 원칙

개발 순서는 다음을 따른다.

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

> **AI 기능부터 만드는 것이 아니라, AI 없이도 동작하는 Financial Case 기반 최소권한 Runtime 차단을 먼저 완성한다.**

그리고 다음 Core Invariant를 개발 중 변경하지 않는다.

```text
Agent Effective Permission
⊆
Employee Authority
```

---

## 2. 권장 Repository 구조

```text
finguard/
├── backend/
│   ├── core-api/
│   │   ├── employee/
│   │   ├── consumer/
│   │   ├── mandate/
│   │   ├── permission/
│   │   ├── case/
│   │   ├── passport/
│   │   ├── context/
│   │   └── dashboard/
│   ├── gateway/
│   │   ├── identity/
│   │   ├── authorization/
│   │   ├── enforcement/
│   │   └── idempotency/
│   ├── agent/
│   ├── mock-finance/
│   └── audit/
├── ai-risk/
│   ├── app/
│   │   ├── prompt/
│   │   ├── behavior/
│   │   ├── feature_builder/
│   │   └── schemas/
│   ├── datasets/
│   ├── models/
│   ├── train/
│   └── evaluate/
├── frontend/
├── policy/
│   ├── finguard_authz.rego
│   └── finguard_authz_test.rego
├── infrastructure/
│   ├── docker-compose.yml
│   └── kubernetes/          # P1
└── docs/
```

---

## 3. 기술 스택

```text
Frontend
→ Vue 3

Main Backend
→ Spring Boot

Gateway
→ Spring Cloud Gateway

Agent
→ Spring AI + LLM API 또는 Simulator

Persistence
→ PostgreSQL + Spring Data JPA

AI Risk Engine
→ Python + FastAPI
→ PyTorch / Transformers
→ scikit-learn / pandas / NumPy / joblib

Policy
→ OPA + Rego

P0 Runtime
→ Docker + Docker Compose

P1 Hardening
→ Kubernetes NetworkPolicy / RBAC / ServiceAccount
```

---

## 4. 팀 역할 분담 — 최종 확정

팀 개발 인원은 4명이며 다음 역할을 고정한다.

## Backend 1 — Financial Context / Permission

담당:

- Employee
- Employee Authority
- Consumer
- Consumer Mandate Seed
- Permission Template
- Financial Case
- Agent Effective Permission 계산
- Task Passport
- Financial Context Resolver
- Scope Status 계산
- JPA / DB

소유 영역 예:

```text
/backend/core-api/employee
/backend/core-api/consumer
/backend/core-api/mandate
/backend/core-api/permission
/backend/core-api/case
/backend/core-api/passport
/backend/core-api/context
```

### Backend 1 핵심 완료 조건

```text
EMP-101은 CUST-9999 조회 권한 보유
BUT
CUST-1001 Case Passport는 CUST-1001만 허용
```

이 상태를 DB/Context Resolver 수준에서 재현한다.

---

## Backend 2 — FinGuard Core / Policy

담당:

- Spring Cloud Gateway
- Runtime Tool Call Interception
- Verified Agent Identity
- Request ID / Trace ID
- Idempotency
- Authorization Service
- AuthorizationContext
- FastAPI Risk Engine Client
- OPA Client
- Rego Policy
- Loan Review Policy Pack
- `ALLOW / BLOCK` Enforcement
- Fail-closed

소유 영역 예:

```text
/backend/gateway
/backend/gateway/identity
/backend/gateway/authorization
/backend/gateway/enforcement
/policy
```

### Backend 2 핵심 규칙

```text
Context Resolver
→ Scope Status

OPA
→ 최종 PolicyDecision
```

동일 Scope 비교 로직을 Spring Gateway와 Rego에 중복 구현하지 않는다.

---

## Backend 3 — Agent / Mock Finance / Audit

담당:

- AgentRun
- LoanAgent
- Spring AI 또는 Agent Simulator
- Mock Financial API
- Mock 금융 데이터
- Internal Credential 검증
- AuditEvent
- Execution Outcome
- 공격 Scenario
- 최근 Audit 조회 Contract
- Synthetic Agent Log 생성 지원
- Docker Compose 통합 지원

소유 영역 예:

```text
/backend/agent
/backend/mock-finance
/backend/audit
/infrastructure/docker-compose.yml
```

Synthetic Data는 보조 역할이며 Backend 개발이 주 업무다.

---

## Frontend & AI

### Frontend

P0 화면을 3개로 제한한다.

1. LoanAgent 실행 / Financial Case 화면
2. Employee Authority vs Agent Effective Permission 화면
3. Security Dashboard + 위험 상세

담당:

- Vue 3
- AgentRun 생성 UI
- Authority 비교 UI
- Risk / Decision / Scope 표시
- Dashboard Filter / Detail

### AI

담당:

- FastAPI Risk Engine
- Prompt Injection Detector
- 한국어 금융 평가 데이터
- Synthetic Dataset 설계
- Feature Builder
- Isolation Forest 학습·평가·추론
- Calibration / Threshold
- AI Evaluation

소유 영역:

```text
/frontend
/ai-risk
```

### Frontend & AI 범위 제한

P0에서 다음은 구현하지 않는다.

- Consumer Mandate CRUD UI
- PII / Response Inspection UI
- Human Approval UI
- Kubernetes 관리 UI

AI 실험과 핵심 3화면 완성도를 우선한다.

---

## 5. Phase 0 — Contract Freeze

개발 전 다음을 확정한다.

```text
EmployeeAuthority
ConsumerMandate
PermissionTemplate
FinancialCase
TaskPassport
AgentRun
ToolCallAttempt
ExecutionOutcome
ScopeStatus
AiRiskResult
AuthorizationContext
PolicyDecision
AuditEvent
```

추가로:

- Enum
- Reason Code
- Endpoint
- Timeout
- Fail-closed
- Request ID / Idempotency
- Behavior Feature
- AI Threshold Config Name

Contract 변경은 팀 합의 후 문서를 먼저 수정한다.

---

## 6. Phase 1 — 독립 Mock 개발

### Backend 1

- Employee / Authority Seed
- Consumer / Mandate Seed
- Permission Template
- Financial Case
- Task Passport 계산 Skeleton
- Context Resolver Skeleton

### Backend 2

- Gateway Skeleton
- Service Credential 인증
- Mock ScopeStatus 입력
- OPA 실행환경
- 기본 Rego Policy

### Backend 3

- AgentRun
- LoanAgent / Simulator
- Mock Financial API
- Mock 금융 데이터
- Audit Schema
- Internal Credential 검증

### Frontend & AI

- Vue Mock UI 3화면
- FastAPI Skeleton
- Prompt Detector Interface
- Behavior Feature Schema
- Isolation Forest 실험환경

---

## 7. Phase 2 — Core Permission / Case-aware Authorization

AI 없이 다음 흐름을 먼저 완성한다.

```text
Employee Authority
∩ Permission Template
∩ Financial Case
∩ Consumer Mandate
        ↓
Task Passport
        ↓
Tool Call
        ↓
Scope Status
        ↓
OPA
        ↓
ALLOW / BLOCK
```

### 필수 테스트

#### 정상

```text
EMP-101
Case=CUST-1001
Request=CUST-1001
→ ALLOW
```

#### 핵심 공격

```text
EMP-101은 CUST-9999 조회 가능
Case=CUST-1001
Request=CUST-9999

employeeAuthority=OK
customerScope=VIOLATION
→ BLOCK
```

### 완료 기준

- Case 밖 고객 요청은 Mock Finance 호출 0회
- Passport 밖 Tool/Data 요청은 0회
- Mandate 밖 Data 요청은 0회
- OPA Reason Code 확인

---

## 8. Phase 3 — Gateway / Identity / Audit

구현:

```text
Request Envelope
Request ID / Trace ID
PROCESSING Audit
Verified Agent Identity
Passport Binding
Idempotency
Runtime Enforcement
Execution Outcome
```

완료 기준:

- 인증 실패도 Audit에 남는다.
- 다른 Agent Passport가 차단된다.
- 동일 Request ID로 Downstream 최대 1회 실행된다.
- Audit 저장 실패 시 금융 API를 호출하지 않는다.
- LoanAgent가 Mock Finance를 직접 호출하면 Internal Credential 검증으로 실패한다.

---

## 9. Phase 4 — Prompt Injection

구현:

```text
입력 정규화
한국어 / 영어 Rule Detection
Prompt 분류 모델
Risk 결합
Threshold
Model Version
평가 코드
```

완료 기준:

- 정상/공격 Test 성능 기록
- False Positive 분석
- 공격 입력 `PROMPT_INJECTION` BLOCK
- Prompt 원문 Audit 미저장
- Prompt Detector가 공격을 놓친 Defense-in-Depth 시나리오에서도 Case Rule 차단

---

## 10. Phase 5 — Isolation Forest

구현:

```text
Synthetic Agent Log
ToolCallAttempt / ExecutionOutcome 분리
Feature Builder
Isolation Forest Train
Validation Calibration
alertThreshold
criticalThreshold
hardRequestLimit1m
Runtime Inference
```

### 핵심 AI 독립 시나리오

```text
Employee Scope = OK
Case = OK
Mandate = OK
Tool/Data = OK
Hard Limit = 미초과
behaviorRisk = CRITICAL

→ AI Risk로 BLOCK
```

### 완료 기준

- 학습/Runtime 동일 Feature Builder
- Train/Validation/Test 분리
- Precision / Recall / F1 / FPR 기록
- AI Alert는 ALLOW + Flag
- AI Critical은 Scope 정상에서도 BLOCK
- Hard Limit은 AI와 독립적으로 BLOCK

---

## 11. Phase 6 — Frontend Integration

P0 화면:

```text
1. LoanAgent 실행
2. Employee Authority vs Agent Effective Permission
3. Security Dashboard
```

완료 기준:

- 정상 ALLOW 표시
- Case Scope BLOCK 표시
- Prompt Risk 표시
- Behavior Alert / Critical 표시
- Scope Status 표시
- Reason Code 표시
- Downstream 도달 여부 표시
- Vue가 DB를 직접 조회하지 않음

---

## 12. Phase 7 — Docker Compose / End-to-End

Docker Compose 대상:

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

최종 E2E:

- 정상 요청
- 직원 권한은 있으나 Case 밖 고객 요청
- Prompt Injection
- Prompt Detector Miss + Case Rule 방어
- Tool/Data/Mandate 위반
- Passport 만료
- Agent Identity 불일치
- Behavior Alert
- Behavior Critical AI-only BLOCK
- Hard Limit Rule BLOCK
- Risk/OPA 장애
- Mock Finance 장애
- Gateway 우회

---

## 13. Phase 8 — P1 Kubernetes Hardening

P0 완료 후 시간과 환경이 허용할 때만 진행한다.

```text
Namespace
ServiceAccount
automountServiceAccountToken=false
Default Deny NetworkPolicy
Allow NetworkPolicy
RBAC 최소권한
Cluster DNS Egress
```

P1 완료 기준:

- LoanAgent → Gateway 허용
- LoanAgent → Mock Finance 직접 접근 차단
- LoanAgent → PostgreSQL 차단
- 불필요한 Kubernetes API 권한 차단

Kubernetes 미완성은 P0 실패로 간주하지 않는다.

---

## 14. 데이터베이스 권장 순서

```text
1. employees
2. employee_authorities
3. consumers
4. consumer_mandates
5. permission_templates
6. financial_cases
7. task_passports
8. secured_agent_inputs
9. agent_runs
10. audit_events
```

모든 Timestamp는 Timezone을 포함한다.

---

## 15. OPA 개발 규칙

- 기본 결정은 BLOCK이다.
- 모든 BLOCK은 1개 이상의 Reason Code를 반환한다.
- Rule마다 정상/위반 Test를 작성한다.
- Policy Version을 응답과 Audit에 포함한다.
- Threshold를 Rego 내부 Magic Number로 중복하지 않는다.
- Undefined Decision을 ALLOW로 해석하지 않는다.
- Scope 비교는 Context Resolver 결과를 사용한다.
- Rego에서 동일 Customer/Tool/Data 비교를 다시 구현하지 않는다.

---

## 16. AI 개발 규칙

- Dataset 생성 코드와 결과를 버전 관리한다.
- 학습/추론 코드를 분리한다.
- Feature Builder는 하나만 유지한다.
- Random Seed를 고정한다.
- 모델 ID/Revision을 고정한다.
- Threshold 선정 근거를 Validation 결과로 저장한다.
- Model 오류를 낮은 Risk로 대체하지 않는다.
- Prompt는 Recall/Precision/F1/FPR/공격유형 Recall을 기록한다.
- Isolation Forest는 Recall/FPR/Cold Start를 기록한다.
- Behavior Risk를 공격 확률이라고 표현하지 않는다.
- 전체 시스템에서는 Authorization Latency P50/P95도 측정한다.

---

## 17. Audit 개발 규칙

- 최소 Envelope 확인 후 PROCESSING Audit을 저장한다.
- 최종 상태는 `COMPLETED / ERROR`다.
- `ALLOW / BLOCK / ERROR`를 모두 기록한다.
- `downstreamReached`와 `responseReleased`를 구분한다.
- 원본 Prompt/금융 응답은 저장하지 않는다.
- Model/Feature/Policy Version을 기록한다.
- Dashboard 외부에서 Audit 수정/삭제 API를 제공하지 않는다.

---

## 18. Pull Request 단위 예시

```text
PR 1: Employee / Authority / Consumer / Mandate
PR 2: Permission Template / Case / Passport
PR 3: AgentRun / LoanAgent / Mock Finance
PR 4: Gateway / Verified Identity / Audit
PR 5: Context Resolver / Scope Status
PR 6: OPA / Enforcement
PR 7: Prompt Injection
PR 8: Behavior AI
PR 9: Vue 3화면
PR 10: Docker Compose / E2E
PR 11: Kubernetes Hardening (P1)
```

각 PR은 관련 Contract/Test 문서 변경을 포함한다.

---

## 19. Definition of Done

### 일반 기능

- `01-feature-spec.md` 충족
- `04-api-contract.md` 준수
- `06-common-conventions.md` 준수
- 정상/차단/오류 흐름 구현
- 모든 결과 Audit 저장
- Verified Identity와 서버 Context 사용
- 동일 Request ID 중복 실행 방지
- Fail-closed 테스트
- 원문 민감 데이터 Audit 미저장

### 권한 기능

- `Agent Effective Permission ⊆ Employee Authority`
- Case 밖 Consumer BLOCK
- Template/Passport 밖 Tool/Data BLOCK
- Mandate 밖 Data BLOCK
- Scope Status와 PolicyDecision 책임 분리

### AI 기능

- Prompt Dataset / 평가 기록
- Synthetic Behavior Dataset 생성 방식 기록
- Train/Validation/Test 분리
- 동일 Feature Builder
- Model/Feature/Dataset Version 기록
- Calibration/Threshold 근거 기록
- Precision/Recall/F1/FPR 기록
- Scope 정상 + Hard Limit 미초과 + AI Critical BLOCK 시나리오 통과

### Frontend

- 3개 P0 화면 동작
- Dashboard DB 직접 접근 없음
- Scope/AI/Decision/Reason 표시

### 배포

- Docker Compose로 P0 재현 가능
- K8s는 P1이므로 P0 DoD에 포함하지 않음

---

## 20. 최종 데모 순서

1. EMP-101의 넓은 Employee Authority 표시
2. CUST-1001 / LOAN_REVIEW AgentRun 시작
3. Agent Effective Permission / Task Passport 표시
4. `CREDIT_SCORE_READ(CUST-1001)` → ALLOW
5. Mock Finance 호출 횟수 증가 확인
6. `CREDIT_SCORE_READ(CUST-9999)` 공격
7. `employeeAuthority=OK`, `customerScope=VIOLATION` 표시
8. OPA `CASE_SCOPE_VIOLATION` BLOCK
9. Mock Finance 미도달 확인
10. Prompt Injection 공격 실행 및 Prompt Risk 표시
11. Prompt Detector Miss 모드에서도 Case Rule 차단 확인
12. Scope 정상 / Hard Limit 미초과 Behavior Critical Scenario 실행
13. Isolation Forest `BEHAVIOR_ANOMALY` 단독 BLOCK 확인
14. Hard Limit Rule BLOCK과 AI BLOCK의 차이 설명
15. Dashboard에서 ALLOW/BLOCK/ERROR와 근거 확인
16. LoanAgent의 Mock Finance 직접 호출 실패 확인
