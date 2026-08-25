# FinGuard MVP 개발 가이드 — 2026.08.17 회의 반영

## 1. 개발 원칙

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

> AI부터 만드는 것이 아니라, AI 없이도 동작하는 Financial Case 기반 최소권한 Runtime 차단을 먼저 완성한다.

```text
Agent Effective Permission ⊆ Employee Authority
```

### 1.1 로컬 개발 환경

**JDK 21이 `JAVA_HOME`으로 잡혀 있어야 한다.** Gradle toolchain은 `build.gradle.kts`에서 21로 고정돼
있지만, **Gradle 자체를 띄우는 JVM은 `JAVA_HOME`이 정한다.** 이 값이 없으면 `PATH`의 아무 `java`나
잡히고, 그게 17 미만이면 빌드가 시작조차 못 한다.

```text
> Dependency requires at least JVM runtime version 17. This build uses a Java 11 JVM.
```

확인:

```bash
./gradlew -q javaToolchains
```

설정 (한 번만):

```bash
# Windows — 새 터미널부터 적용된다
setx JAVA_HOME "C:\Program Files\Java\jdk-21"

# macOS / Linux — 셸 설정 파일에 추가
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
```

Docker가 필요한 것: PostgreSQL / OPA(`infrastructure/docker-compose.yml`), OPA 정책 테스트,
그리고 Core의 영속화 테스트(Testcontainers). Docker Desktop이 떠 있지 않으면 이들이 실패한다.

---

## 2. Repository 구조

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
│   │   ├── audit/
│   │   ├── security-event/
│   │   ├── behavior-history/
│   │   └── dashboard/
│   ├── gateway/
│   │   ├── identity/
│   │   ├── authorization/
│   │   ├── enforcement/
│   │   └── idempotency/
│   ├── agent/
│   └── mock-finance/
├── ai-risk/
│   ├── app/prompt/
│   ├── app/behavior/
│   ├── app/feature_builder/
│   ├── datasets/
│   ├── models/
│   └── evaluate/
├── frontend/
├── policy/
├── infrastructure/
│   ├── docker-compose.yml
│   └── kubernetes/      # P1 only
└── docs/
```

**Gateway용 DB Repository/DAO 폴더는 만들지 않는다.**

---

## 3. Module Ownership

### Backend 1 — Financial Context / Permission / Core Persistence

- Employee / Authority
- Consumer / Mandate Seed
- Permission Template
- Financial Case
- Effective Permission / Task Passport
- Context Resolver / Scope Status
- Prompt Risk Snapshot Persistence
- Business Audit / SecurityAuthEvent Persistence
- Behavior History Read API
- Dashboard Read API
- JPA / PostgreSQL

### Backend 2 — Gateway / OPA / Enforcement

- Spring Cloud Gateway
- Tool Call Interception
- Request Size / Rate Limit
- Agent Credential / Verified Identity
- Request ID / Trace ID / Idempotency
- Core Internal API Clients
- AI Risk Client
- AuthorizationContext
- OPA Client / Rego
- Enforcement / Fail-closed
- **DB 직접 접근 금지**

### Backend 3 — Agent / Mock Finance / Audit Contract

- AgentRun / LoanAgent / Simulator
- Mock Financial API / Mock 금융데이터
- ToolCallAttempt / ExecutionOutcome Contract
- AuditEvent Contract 설계 지원
- 정상/공격 Scenario
- Synthetic Agent Log 생성 지원
- Docker Compose 통합 지원

### Frontend & AI

Frontend:

```text
1. LoanAgent 실행 / Financial Case
2. Employee Authority vs Agent Effective Permission
3. Security Dashboard
```

AI:

- FastAPI Risk Engine
- Prompt Injection Detector
- Prompt Evaluation Set / 평가
- Synthetic Behavior Dataset
- Feature Builder
- Isolation Forest
- Calibration / Threshold

---

## 4. Scenario Ownership

Module Owner와 별도로 E2E 완성 책임자를 둔다.

```text
Backend 1
→ 정상 ALLOW + Identity / Passport E2E

Backend 2
→ Case BLOCK + Idempotency E2E

Backend 3
→ Tool / Data / Mandate + Fail-closed E2E
```

원칙:

> Scenario Owner는 시나리오 완성을 책임지지만 다른 Module Owner의 핵심 코드를 독단적으로 수정하지 않는다.

현재 Contract 변경은 `/docs/04-api-contract.md` 합의 → 구현 → PR Review 순서로 진행한다. OpenAPI Freeze 이후에는 `/docs/api-contract.yaml`도 함께 갱신한다.

---

## 5. Phase 0 — Contract Freeze

필수 Schema:

```text
EmployeeAuthority
ConsumerMandate
PermissionTemplate
FinancialCase
TaskPassport
AgentRun
PromptRiskSnapshot
ToolCallAttempt
ExecutionOutcome
ScopeStatus
AiRiskResult
AuthorizationContext
PolicyDecision
AuditEvent
SecurityAuthEvent
```

필수 합의:

- Enum / Reason Code
- Internal API Endpoint
- Timeout / Fail-closed
- Request ID / Idempotency
- Behavior Feature
- Prompt 입력 재검사 기준
- DB Ownership

---

## 6. Phase 1 — Independent Mock

현재 개발 사이클의 핵심 단계다. 아래 담당별 Skeleton / Mock 체크리스트를 따른다.

### Backend 1

- Entity / Seed
- Effective Permission / Passport Skeleton
- Context Resolver Skeleton
- Core Context/Audit/Security/History API Skeleton

### Backend 2

- Gateway / Credential / Rate Limit Skeleton
- Core Client Mock
- OPA / Rego / PolicyDecision Skeleton
- Mock ScopeStatus로 BLOCK

### Backend 3

- AgentRun / LoanAgent 또는 Simulator
- Mock Finance
- ToolCallAttempt / ExecutionOutcome / Audit Contract
- Scenario 초안

### Frontend & AI

- Vue 3화면 Mock
- FastAPI Skeleton
- Behavior Feature Schema
- Synthetic 샘플
- Isolation Forest fit/inference
- Prompt Detector Interface는 선택 사항

Kubernetes는 이번 단계에서 구현하지 않는다.

---

## 7. Phase 2 — Core Permission / Case-aware Authorization

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

핵심 공격:

```text
EMP-101은 CUST-9999 조회 가능
Current Case = CUST-1001
Request = CUST-9999

employeeAuthority = OK
customerScope = VIOLATION
→ CASE_SCOPE_VIOLATION
→ BLOCK
```

---

## 8. Phase 3 — Authentication / Audit / Idempotency

처리 순서:

```text
Size / Envelope
→ Rate Limit
→ Credential Verification
```

인증 성공:

```text
Verified Identity
→ Core Business Audit API
→ PROCESSING Audit
→ Authorization
```

인증 실패:

```text
Core Security Event API
→ SecurityAuthEvent
→ 종료
```

완료 기준:

- 인증 실패 요청은 Business Audit을 만들지 않는다.
- 인증 실패도 최소 SecurityAuthEvent로 DB에 남는다.
- Gateway가 DB를 직접 읽거나 쓰지 않는다.
- Audit 선저장 실패 시 Downstream 0회다.
- 중복 Request ID Downstream 실행 최대 1회다.

---

## 9. Phase 4 — Prompt Injection

```text
새 입력 유입
→ Prompt Detector
→ PromptRiskSnapshot
```

완료 기준:

- 새 Prompt / Document는 검사된다.
- 동일 `inputHash + modelVersion`은 Tool Call마다 재추론하지 않는다.
- 새로운 입력 추가 시 다시 검사한다.
- 원문 Prompt/Document는 Audit에 저장하지 않는다.
- Defense-in-Depth에서 Prompt Detector miss라도 Case Rule이 공격을 막는다.

Prompt Detector는 사전학습 후보를 비교평가하며, Development/Validation Set으로 선택·Threshold를 정하고 Held-out Test로 최종 평가한다.

---

## 10. Phase 5 — Behavior AI

```text
Core Behavior History
+
Current ToolCallAttempt
↓
FastAPI Feature Builder
↓
Isolation Forest
↓
behaviorRisk
```

핵심 규칙:

- Tool Call마다 최신 Behavior Risk 계산
- 현재 Attempt의 미래 Outcome Field 사용 금지
- Gateway/FastAPI DB 직접 조회 금지
- Isolation Forest raw score를 확률이라고 표현하지 않음

AI 독립 데모:

```text
모든 Scope = OK
Hard Limit = 미초과
Behavior Risk = CRITICAL
→ BEHAVIOR_ANOMALY
→ BLOCK
```

---

## 11. Phase 6 — Frontend Integration

P0 3화면만 우선한다.

- LoanAgent 실행 / Case
- Authority vs Effective Permission
- Security Dashboard

표시:

```text
Scope Status
Prompt Risk Snapshot
Behavior Risk
Decision
Reason Code
Downstream Reached
```

Vue는 DB를 직접 조회하지 않는다.

---

## 12. Phase 7 — Docker Compose E2E

```text
Spring Core API
Spring Cloud Gateway
LoanAgent
Mock Financial API
FastAPI AI Risk Engine
OPA
PostgreSQL
Vue Frontend
```

P0 E2E:

- 정상 ALLOW
- Case 밖 고객 BLOCK
- Tool/Data/Mandate BLOCK
- 인증 실패 Security Event
- Passport / Identity BLOCK
- Prompt Injection
- 동일 Prompt Snapshot 재사용
- Behavior Alert / Critical
- Hard Limit
- Core/Risk/OPA 장애 Fail-closed
- Audit 선저장 실패 Downstream 0회

Gateway 우회에 대한 NetworkPolicy 보장은 P0 Release Gate가 아니다.

---

## 13. Phase 8 — P1 Kubernetes Hardening

P0 완료 후 선택적으로 진행한다.

```text
Namespace
ServiceAccount
RBAC 최소권한
Default Deny NetworkPolicy
Allow NetworkPolicy
```

검증:

```text
LoanAgent → Gateway      ALLOW
LoanAgent → Mock Finance DENY
LoanAgent → PostgreSQL   DENY
Gateway   → PostgreSQL   DENY
Core      → PostgreSQL   ALLOW
```

Kubernetes 미완성은 P0 실패가 아니다.

---

## 14. DB 권장 테이블

```text
employees
employee_authorities
consumers
consumer_mandates
permission_templates
financial_cases
task_passports
secured_agent_inputs
prompt_risk_snapshots
agent_runs
audit_events
security_auth_events
```

DB 접근은 Core Persistence Layer만 수행한다.

---

## 15. 개발 규칙

### OPA

- 기본 BLOCK
- 모든 BLOCK에 Reason Code
- Scope raw 비교 중복 금지
- Threshold Magic Number 중복 금지

### AI

- Feature Builder 하나 유지
- 모델/데이터/Feature 버전 관리
- Prompt는 Development/Validation vs Held-out Test 분리
- Behavior는 Train/Validation/Test 분리
- Threshold 선정 근거 저장

### Audit

- 인증 성공 후 Business Audit 생성
- 인증 실패는 SecurityAuthEvent
- Prompt/금융 원문 저장 금지
- `downstreamReached` / `responseReleased` 구분
- Gateway DB 직접 접근 금지

### Git / File Ownership

- 모든 작업은 Issue → Branch → PR → Review
- 공통 Contract 파일은 합의 후 지정 편집자 1명이 수정
- 다른 Module Owner의 핵심 영역 대규모 변경은 Owner Review 필수

---

## 16. Definition of Done

### P0 공통

- `01-feature-spec.md` 충족
- `04-api-contract.md` 준수
- `06-common-conventions.md` 준수
- Core Invariant 유지
- Gateway DB 직접 접근 없음
- 인증 실패/성공 Audit 경계 준수
- Fail-closed 테스트
- Docker Compose 재현

### 권한

- Case 밖 Consumer BLOCK
- Passport/Template 밖 Tool/Data BLOCK
- Mandate 밖 Data BLOCK
- Scope Status와 OPA 책임 분리

### AI

- Prompt 새 입력 검사 + 동일 입력 Snapshot 재사용
- Prompt 평가와 Behavior 학습 데이터 분리 방식 문서화
- Behavior Critical AI-only BLOCK 데모
- FPR / Recall 등 평가

### Frontend

- 핵심 3화면
- DB 직접 접근 없음
- Scope/Risk/Decision/Reason 표시

---

## 17. 최종 데모 순서

1. EMP-101의 넓은 Employee Authority 표시
2. CUST-1001 Case / Passport 생성
3. Agent Effective Permission 표시
4. 정상 `CREDIT_SCORE_READ(CUST-1001)` ALLOW
5. `CREDIT_SCORE_READ(CUST-9999)` → `customerScope=VIOLATION`
6. OPA `CASE_SCOPE_VIOLATION` BLOCK + Downstream 0회
7. 새 악성 Prompt/Document 입력 → Prompt Risk 생성
8. 동일 입력의 후속 Tool Call에서 Prompt Detector 재추론 없이 Snapshot 재사용
9. Prompt Detector miss + Case Rule 차단
10. Scope 정상 / Hard Limit 미초과 Behavior Critical → AI-only BLOCK
11. 인증 실패 요청 → Business Audit 없음 + SecurityAuthEvent 존재
12. Dashboard에서 ALLOW/BLOCK/ERROR 근거 확인
13. Docker Compose 재현
14. Kubernetes 우회 방지는 P1 로드맵으로 설명
