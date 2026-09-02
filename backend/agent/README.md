# LoanAgent

Backend 3 소유 영역입니다. P0에서는 Spring AI 연동 또는 결정론적 Simulator를 선택할 수 있습니다.

- Core가 발급한 AgentRun을 Case/Passport/Input Reference에 연결해 실행합니다.
- 금융 Tool은 Gateway endpoint만 호출합니다.
- Body의 Agent ID나 권한 목록을 권한 근거로 사용하지 않습니다.
- Mock Financial API를 직접 호출하지 않습니다.

## 현재 구현 상태

P0 통합 전 정상 요청과 Case/Tool/Data/Mandate Scope 공격 요청을 반복 가능하게 생성하는 결정론적
Simulator를 제공합니다. 실제 LLM은 아직 사용하지 않습니다.

P0 Runtime Contract입니다. P1에서 실제 Agent Runtime으로 교체할 때 Endpoint와 DTO를
`docs/04-api-contract.md` 기준으로 테스트와 함께 수정합니다.

```http
POST /internal/v1/agent-simulations
X-FinGuard-Internal-Credential: <internal-service-credential>
Content-Type: application/json
```

```json
{
  "agentRunId": "RUN-001",
  "passportId": "PASS-001",
  "scenario": "NORMAL_CREDIT_SCORE"
}
```

지원 Scenario:

| Scenario | Gateway 요청 대상 | 목적 |
|---|---|---|
| `NORMAL_CREDIT_SCORE` | `CUST-1001` | 정상 ALLOW 흐름 |
| `CASE_SCOPE_ATTACK` | `CUST-9999` | 현재 Case 밖 고객 조회 시도 |
| `NORMAL_INCOME` | `CUST-1001` | INCOME_READ / INCOME 정상 조회 |
| `NORMAL_DEBT` | `CUST-1001` | DEBT_READ / DEBT 정상 조회 |
| `TOOL_SCOPE_ATTACK` | `CUST-1001` | INCOME_READ / INCOME, Passport 밖 Tool 시도 |
| `DATA_SCOPE_ATTACK` | `CUST-1001` | CREDIT_SCORE_READ / CREDIT_SCORE + INCOME, 추가 Data 시도 |
| `MANDATE_SCOPE_ATTACK` | `CUST-1001` | DEBT_READ / DEBT, Mandate 밖 Data 시도 |

추가 Scenario는 #60의 계약 확장 제안이며 소비자 Review가 필요합니다.
Simulator는 모든 Scenario를 다음 Gateway Contract로 변환합니다 (정상 신용점수 예시).

```http
POST /gateway/v1/tool-calls
Authorization: Bearer <agent-service-credential>
X-Request-Id: <uuid>
Traceparent: <w3c-trace-context>
```

```json
{
  "agentRunId": "RUN-001",
  "passportId": "PASS-001",
  "tool": "CREDIT_SCORE_READ",
  "targetConsumerId": "CUST-1001",
  "requestedData": ["CREDIT_SCORE"],
  "action": "READ"
}
```

`employeeId`, `agentId`, `caseId`, 권한 목록은 Runtime 요청 Body에 넣지 않습니다.
Agent가 Mock Finance를 직접 호출하는 Client나 URL도 갖지 않습니다.

## 로컬 실행

실제 운영 Credential이 아닌 로컬 개발용 값을 사용합니다.

```bash
export AGENT_SERVICE_CREDENTIAL=local-agent-only
export FINGUARD_INTERNAL_CREDENTIAL=local-internal-only
export GATEWAY_BASE_URL=http://localhost:8081
./gradlew :backend:agent:bootRun
```

기본 Agent Port는 `8082`, 기본 Gateway Port는 `8081`입니다. 현재 Gateway 기능이 아직
연결되지 않은 환경에서는 자동 테스트가 Mock Gateway Client를 사용해 요청 Contract를
검증합니다.

## Core Client 경계

> 이 브랜치는 #59 이전 기준에서 분기되었습니다. 아래 Core Client 설명은 현재 기반 코드의
> 상태이며, 목표 호출 방향은 Core → Agent입니다. #59 / PR #78에서 기존 Agent Core Client를
> 제거하고 #74 / PR #75가 Core 오케스트레이션을 담당합니다. #60은 이를 중복 구현하지 않습니다.

`POST /api/v1/agent-runs`와 다음 데이터의 발급·저장 책임은 Core에 있습니다.

- Financial Case
- Task Passport
- Secured Input / PromptRiskSnapshot
- AgentRun ID와 상태

Agent 모듈은 `CoreAgentRunClient`를 통해 발급을 요청하고 Core가 반환한 `agentRunId`,
`caseId`, `passportId`, `inputRefs`를 실행 참조로 사용합니다. 로컬 ID를 만들거나 입력 원문을
별도로 저장하지 않으며 AgentRun 상태도 로컬에서 전환하지 않습니다.

Core 주소는 `CORE_API_BASE_URL`로 설정하며 기본값은 `http://localhost:8080`입니다.

실행 흐름:

```text
LoanAgent / Simulator
→ CoreAgentRunClient
→ Core POST /api/v1/agent-runs
→ Core가 Case / Passport / AgentRun 발급
→ 반환된 Passport 참조로 Gateway를 통한 Tool Call 실행
```

## 검증

```bash
./gradlew :backend:agent:check
```

테스트는 정상/공격 Scenario 변환, 내부 Credential 거부, Gateway Header와 Body,
`ALLOW/BLOCK` 구분, Timeout/5xx/잘못된 응답의 명시적 실패를 검증합니다.

## 공격 재현 조건 및 통합 의존성

Scenario 이름 자체는 권한 위반의 증거가 아닙니다. Core가 발급한 유효한 Run/Passport를
요청에 넣고 다음 제한 조건을 서버 Fixture로 준비해야 합니다.

| 공격 | 서버 Fixture 조건 | 기대 Reason Code |
|---|---|---|
| Case | Case/Passport 고객은 CUST-1001, 요청 고객은 CUST-9999 | CASE_SCOPE_VIOLATION |
| Tool | Passport의 allowedTools에 INCOME_READ 없음; INCOME Data는 허용 | TOOL_SCOPE_VIOLATION |
| Data | CREDIT_SCORE_READ 허용; Passport의 allowedData에는 CREDIT_SCORE만 있음 | DATA_SCOPE_VIOLATION |
| Mandate | 현재 Mandate의 allowedData에 DEBT 없음 | MANDATE_SCOPE_VIOLATION |

그 외 Authority/Template/유효기간/Agent binding/Risk/Rate Limit 조건은 정상으로 준비합니다.
Mandate는 Effective Permission의 교집합에 포함되므로 Mandate 공격은 DATA_SCOPE_VIOLATION도
함께 발생할 수 있습니다. 이를 숨기거나 단일 Reason Code로 덮어쓰지 않습니다.
일반 Seed가 세 Tool/Data를 모두 허용하면 공격 Scenario도 ALLOW가 될 수 있으며,
Agent는 이를 임의로 BLOCK으로 바꾸지 않습니다. NORMAL_INCOME/TOOL_SCOPE_ATTACK과
NORMAL_DEBT/MANDATE_SCOPE_ATTACK의 요청은 각각 같고, 사용하는 서버 Context가 다릅니다.

`AgentScenarioMappingTest`는 7개 Scenario의 반복 가능한 최소 Body 변환, 응답 그대로 전달,
ERROR 유지, 설정 불변성을 검증합니다. `AgentAttackScenarioIntegrationTest`는 실제 Agent HTTP와
Mock Gateway로 정상 3개/공격 4개의 호출 1회 및 BLOCK/Reason Code 전달을 검증합니다.
Mock은 Scope나 OPA를 재구현하지 않습니다. 실제 금융 downstream 0회 검증은 #75·#77 통합 및
전용 Core Fixture 준비 후 필요하며, 이 테스트를 전체 인가 E2E로 간주하지 않습니다.

PR 검토 요청:

- Backend 1: #75의 Core Scenario Enum에 새 5개 값을 수용할지 확인하고 제한된 Fixture를 준비합니다.
- Backend 2: #77에서 실제 Scope 위반 및 복수 Reason Code, 금융 downstream 미호출을 검증합니다.
- #59 / PR #78 병합 후 해당 변경을 반영하고 Agent 회귀 테스트를 다시 실행합니다.
