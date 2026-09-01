# LoanAgent

Backend 3 소유 영역입니다. P0에서는 Spring AI 연동 또는 결정론적 Simulator를 선택할 수 있습니다.

- Core가 발급한 AgentRun을 Case/Passport/Input Reference에 연결해 실행합니다.
- 금융 Tool은 Gateway endpoint만 호출합니다.
- Body의 Agent ID나 권한 목록을 권한 근거로 사용하지 않습니다.
- Mock Financial API를 직접 호출하지 않습니다.

## 현재 구현 상태

P0 통합 전 정상 요청과 Case Scope 공격 요청을 반복 가능하게 생성하는 결정론적
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

Simulator는 두 Scenario 모두 다음 Gateway Contract로 변환합니다.

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

## Core 발급 참조 경계

`POST /api/v1/agent-runs`와 다음 데이터의 발급·저장 책임은 Core에 있습니다.

- Financial Case
- Task Passport
- Secured Input / PromptRiskSnapshot
- AgentRun ID와 상태

P0 Simulator는 Core 또는 상위 Orchestrator가 AgentRun 생성 응답에서 얻은 `agentRunId`와
`passportId`를 요청으로 받습니다. Agent는 이 참조를 로컬에서 생성하거나 저장하지 않고,
AgentRun 상태를 전환하거나 입력 원문을 보관하지 않습니다.

실행 흐름:

```text
Operator / 상위 Orchestrator
→ Core POST /api/v1/agent-runs
→ Core가 Case / Passport / Input Reference / AgentRun 발급
→ Core 발급 agentRunId / passportId로 Simulator 호출
→ Agent가 동일 참조로 Gateway Tool Call 실행
```

Core 생성 실패·Timeout 시 Simulator를 호출하지 않는 오케스트레이션의 소유 계층과 테스트
위치는 별도 팀 합의가 필요합니다. Agent가 Operator Credential을 보유해 Core 생성 API를
직접 호출하는 흐름은 현재 P0 Credential Contract에 포함되지 않습니다.

## 검증

```bash
./gradlew :backend:agent:check
```

테스트는 Core 발급 참조의 정확한 전달, 필수 참조 누락 시 Gateway 미호출,
정상/공격 Scenario 변환, 내부 Credential 거부, Gateway Header와 최소 Runtime Body,
`ALLOW/BLOCK` 구분, Timeout/5xx/잘못된 응답의 명시적 실패를 검증합니다.
