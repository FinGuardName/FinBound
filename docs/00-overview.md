# FinGuard MVP Overview

## 1. 프로젝트 정의

FinGuard는 **금융기관 직원이 가진 넓은 업무권한을 AI Agent에게 그대로 상속하지 않고**, 현재 수행 중인 금융업무·Financial Case·대상 소비자·허용 Tool/Data 범위에 따라 Agent의 실효권한을 최소화한 뒤, AI Risk와 정책을 결합해 Tool Call 실행 직전에 `ALLOW` 또는 `BLOCK`을 결정하는 금융 AI Agent Runtime Authorization Gateway다.

핵심 질문은 다음과 같다.

> **이 직원이 원래 할 수 있는 업무라고 해도, 지금 이 Agent가 현재 맡은 Financial Case를 위해 이 고객의 이 데이터를 이 Tool로 사용하는 것이 정당한가?**

핵심 메시지:

> **사람이 할 수 있다고 해서, AI Agent가 지금 그 일을 해도 되는 것은 아니다.**

## 2. Core Invariant

```text
Agent Effective Permission
⊆
Employee Authority
```

AI Risk는 권한을 새로 부여하지 않는다.

```text
AI Risk
→ 권한 유지 / 위험 표시 / 추가 차단
→ 권한 확대 금지
```

최종 권한 계산:

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

- `Employee Authority`: 직원이 원래 수행할 수 있는 최대 업무권한
- `Permission Template`: `LOAN_REVIEW` 업무에 필요한 표준 Tool/Data 범위
- `Financial Case`: 현재 처리 중인 고객·목적·유효시간
- `Consumer Mandate`: 해당 고객이 현재 목적에서 허용한 Data 범위
- `Task Passport`: 위 교집합을 Runtime에서 사용하는 권한 Snapshot

## 3. 핵심 가치

```text
권한 최소화
→ Employee Authority를 Financial Case 단위 Agent 권한으로 축소

입력 보안
→ Prompt Injection 탐지

행동 보안
→ Isolation Forest 이상행동 탐지

정책 판단
→ Scope Status + AI Risk를 OPA에서 조합

실제 집행
→ FinGuard Gateway가 금융 API 도달 여부를 통제

운영 추적
→ PostgreSQL Audit + Read-only Dashboard
```

## 4. 핵심 사용자

| 사용자·시스템 | 요구사항 |
|---|---|
| LoanAgent | 허용된 고객·Tool·Data 범위에서 대출심사를 수행한다. |
| 대출업무 직원/운영자 | 본인 권한보다 좁은 Agent 권한으로 업무를 위임한다. |
| 금융 서비스 개발팀 | Agent가 Gateway를 우회하거나 권한을 임의 확대하지 못하게 한다. |
| 보안 모니터링 사용자 | ALLOW·BLOCK·ERROR와 Scope/AI 근거를 조회한다. |
| 금융소비자 | 대출심사 목적에서 허용된 Data 범위가 Agent 권한 계산에 반영된다. |

## 5. 주요 용어

| 용어 | 정의 |
|---|---|
| Employee Authority | 직원에게 부여된 원래 금융업무 권한. Agent 권한의 상한선 |
| Permission Template | 특정 금융업무에 필요한 표준 Tool/Data 목록 |
| Financial Case | 현재 Agent가 수행하는 고객별 금융업무 Context |
| Consumer Mandate | 현재 업무 목적에서 고객이 허용한 데이터 범위. P0는 Seed Data |
| Agent Effective Permission | 현재 Case에서 Agent에게 실제로 유효한 최소권한 |
| Task Passport | Agent Effective Permission을 구조화한 Runtime Permission Snapshot |
| AgentRun | LoanAgent의 한 번의 업무 실행 단위 |
| Scope Status | Context Resolver가 계산한 각 권한 범위의 `OK / VIOLATION` 상태 |
| Prompt Risk | Prompt Injection 입력 위험 점수 |
| Behavior Risk | 누적 Agent 행동이 정상 분포에서 벗어난 정도를 보정한 Risk Score |
| PolicyDecision | OPA가 반환하는 최종 `ALLOW / BLOCK` 결과 |
| AuditEvent | 요청 접수부터 최종 실행 결과까지의 감사 기록 |

## 6. MVP 업무 범위

### 업무 유형

```text
LOAN_REVIEW
```

### Agent

```text
LOAN-AGENT-01
```

### 금융 Tool

```text
CREDIT_SCORE_READ
INCOME_READ
DEBT_READ
```

### 정책 결과

```text
ALLOW
BLOCK
```

시스템 처리 실패는 정책 결정과 구분해 `ERROR` Outcome으로 기록한다.

## 7. P0 구현 범위

### 권한·Financial Context

- Employee / Employee Authority
- Consumer / Consumer Mandate Seed Data
- Permission Template
- Financial Case
- Agent Effective Permission
- Task Passport
- Financial Context Resolver
- Scope Status

### Runtime

- LoanAgent 1개
- Spring Cloud Gateway 기반 Tool Call Interception
- Gateway Credential 기반 Verified Agent Identity
- AuthorizationContext 생성
- OPA/Rego Policy
- `ALLOW / BLOCK` Enforcement
- Mock Financial API
- Idempotency / Fail-closed

### AI Risk

- Prompt Injection Detection
- Isolation Forest Behavior Anomaly Detection
- Behavior Risk Calibration
- AI 단독 Critical Behavior 차단 시나리오

### Audit / Web

- PostgreSQL Audit
- LoanAgent 실행 화면
- Employee Authority vs Agent Effective Permission 화면
- Security Dashboard / 위험 상세

### Deployment

- Docker Image
- Docker Compose 기반 로컬·데모 실행

## 8. P1 고도화 범위

- Consumer Mandate CRUD / 철회 UI
- PII / Sensitive Data Response Inspection
- `MASK / APPROVAL`
- Human Approval UI
- Kubernetes Namespace / ServiceAccount / RBAC / NetworkPolicy
- OpenSearch / Risk History

Kubernetes는 FinGuard의 핵심 권한 판단 기능이 아니라 **Gateway 우회 방지를 강화하는 Deployment Hardening**으로 취급한다.

## 9. Runtime 시작 전 준비 흐름

```text
직원 / Consumer / 업무 유형 선택
        ↓
Employee Authority 조회
Permission Template 조회
Consumer Mandate 조회
        ↓
Financial Case 생성
        ↓
Agent Effective Permission 계산
        ↓
Task Passport 발급
        ↓
AgentRun 시작
```

Agent는 자신이 사용할 권한 목록을 직접 제출해 확대할 수 없다.

## 10. Runtime 처리 흐름

1. LoanAgent가 Gateway에 금융 Tool Call을 보낸다.
2. Gateway가 최소 Request Envelope를 검증하고 Request ID를 발급한다.
3. Gateway가 Service Credential로 Agent Identity를 검증한다.
4. AuditEvent를 `PROCESSING`으로 저장하고 검증된 Identity를 기록한다.
5. Financial Context Resolver가 Employee Authority, Permission Template, Case, Mandate, Passport를 조회한다.
6. Context Resolver가 각 Scope에 대해 `OK / VIOLATION` 상태를 계산한다.
7. FastAPI AI Risk Engine이 Prompt Risk와 Behavior Risk를 계산한다.
8. Spring Authorization Service가 `Scope Status + AI Risk + Hard Limit`을 `AuthorizationContext`로 구성한다.
9. OPA가 AuthorizationContext를 Rego 정책으로 평가해 `PolicyDecision`을 반환한다.
10. `ALLOW`이면 Gateway가 내부 Credential을 붙여 Mock Financial API를 호출한다.
11. `BLOCK`이면 Mock Financial API에 도달하지 않고 요청을 종료한다.
12. Backend가 Execution Outcome을 AuditEvent에 반영한다.
13. Vue Dashboard는 Spring Read-only API를 통해 Audit을 조회한다.

## 11. Scope Status와 PolicyDecision 책임

```text
Financial Context Resolver
→ 각 Scope의 상태를 계산
→ OK / VIOLATION
→ 최종 ALLOW/BLOCK은 결정하지 않음

OPA
→ Scope Status + AI Risk + Hard Limit을 정책으로 조합
→ 최종 ALLOW/BLOCK 결정
```

동일한 Case/Tool/Data 비교 규칙을 Spring과 Rego 양쪽에 중복 구현하지 않는다.

## 12. 핵심 정책

| 조건 | 결과 |
|---|---|
| Employee/Template/Case/Mandate/Passport/Tool/Data 모두 정상, Risk 낮음 | ALLOW |
| Employee Authority 밖 요청 | BLOCK |
| Case 대상 고객 불일치 | BLOCK |
| Permission Template / Passport 밖 Tool·Data | BLOCK |
| Consumer Mandate 밖 Data | BLOCK |
| Agent Identity / Passport Binding 불일치 | BLOCK |
| Case 또는 Passport 비활성·만료 | BLOCK |
| Prompt Injection 차단 조건 충족 | BLOCK |
| Behavior Risk가 Alert 구간 | ALLOW + `riskFlagged=true` |
| Behavior Risk가 Critical Threshold 이상 | BLOCK |
| Hard Request Limit 초과 | AI와 무관하게 BLOCK |
| Risk Engine 또는 OPA 필수 서비스 오류 | Fail-closed BLOCK |

## 13. 핵심 데모 3개

### A. 금융 Case 기반 최소권한

```text
EMP-101
→ CUST-1001 / CUST-9999 조회 권한 보유

현재 Case
→ CUST-1001 대출심사

Agent 요청
→ CREDIT_SCORE_READ(CUST-9999)

Employee Authority = OK
Permission Template = OK
Case Scope = VIOLATION

→ BLOCK
→ Mock Financial API 도달 0회
```

### B. AI 독립 가치

```text
Employee Scope = OK
Case Scope = OK
Tool/Data Scope = OK
Hard Limit = 미초과

BUT
누적 행동이 정상 분포에서 극단적으로 이탈

Isolation Forest
→ behaviorRisk >= criticalThreshold

→ BEHAVIOR_ANOMALY
→ BLOCK
```

### C. Defense in Depth

```text
악성 문서/입력
→ Prompt Injection 탐지
→ 다른 고객 조회 시도

Prompt Risk = HIGH
Case Scope = VIOLATION

→ BLOCK
→ downstreamReached=false
```

Prompt Detector가 공격을 놓치는 별도 테스트에서도 Case Scope Rule이 요청을 차단해야 한다.

## 14. 기록 원칙

다음 결과를 모두 PostgreSQL에 저장한다.

```text
인증 성공·실패
정상 ALLOW
정책 BLOCK
ERROR
Scope Status
Prompt Risk / 모델 버전
Behavior Risk / Feature·모델 버전
OPA Decision / Policy 버전
Downstream 도달 여부
Agent 응답 반환 여부
Reason Code
```

다음 원문은 Audit에 저장하지 않는다.

```text
원본 Prompt
원본 금융 문서
금융 API Response Payload
실제 개인정보 원문
Service Credential
내부 Secret
```

## 15. 기술 구성

| 영역 | 기술 |
|---|---|
| Frontend | Vue 3 |
| Main Backend | Spring Boot |
| Gateway | Spring Cloud Gateway |
| Agent | Spring AI + LLM API 또는 Simulator |
| DB | PostgreSQL |
| ORM | Spring Data JPA |
| AI Risk Engine | FastAPI |
| Prompt Injection | PyTorch / Hugging Face Transformers + Rule Detection |
| Behavior Detection | scikit-learn Isolation Forest, pandas, NumPy, joblib |
| Policy | OPA + Rego |
| P0 Deployment | Docker + Docker Compose |
| P1 Deployment Hardening | Kubernetes NetworkPolicy / RBAC / ServiceAccount |
| Test | Spring Test, pytest, OPA Test, API Contract / E2E Test |

## 16. 핵심 성공 기준

```text
정상 Tool Call
→ 금융 API 도달 1회
→ Agent 응답 반환
→ ALLOW Audit 저장

Case/권한 위반 Tool Call
→ 금융 API 도달 0회
→ 명확한 Reason Code
→ BLOCK Audit 저장

AI Critical Behavior
→ Rule/Scope 정상
→ Hard Limit 미초과
→ AI Risk로 BLOCK

모든 ALLOW / BLOCK / ERROR
→ Dashboard에서 근거와 함께 조회 가능

전체 서비스
→ Docker Compose로 재현 가능
```
