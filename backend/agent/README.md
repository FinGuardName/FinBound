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

P0 Simulator는 Core가 발급한 `agentRunId`와
`passportId`를 요청으로 받습니다. Agent는 이 참조를 로컬에서 생성하거나 저장하지 않고,
AgentRun 상태를 전환하거나 입력 원문을 보관하지 않습니다.

실행 흐름:

```text
Operator
→ Core POST /api/v1/agent-runs
→ Core가 Case / Passport / Input Reference / AgentRun 발급
→ Core가 발급 agentRunId / passportId로 Simulator 호출
→ Agent가 동일 참조로 Gateway Tool Call 실행
```

Core 생성 실패·Timeout 시 Simulator를 호출하지 않는 오케스트레이션과 해당 테스트는
#74 / PR #75의 Core 담당 범위입니다. #59에는 중복 Core Client·Controller 연결·상태 갱신을
두지 않습니다. Agent는 Operator Credential을 보유하거나 Core 생성 API를 직접 호출하지 않습니다.
동기/비동기 실행 방식과 Core 생성 API의 선택적 `scenario`는 PR #75에서 검토하며,
Agent는 두 Scenario를 기존 Runtime Contract대로 처리합니다.

Case/Input Reference는 Core의 AgentRun에 연결되어 있으며 P0 Simulator Body에는 전달하지
않습니다. 입력 원문을 Agent가 저장하거나 재전송하지도 않습니다. Simulator 호출 후 Timeout은
이미 실행된 금융 요청의 취소를 의미하지 않으므로 자동 재시도를 하지 않습니다.

Gateway의 필수 응답 필드 누락, 잘못된 JSON, `403 + ALLOW`, 금융 결과가 포함된 BLOCK은
`GATEWAY_RESPONSE_INVALID`로 실패합니다. Gateway 시스템 오류는 HTTP 502와 실행 오류 코드로
반환하고 `ALLOW/BLOCK`으로 바꾸지 않으며, 원본 오류 본문을 노출하지 않습니다.

## 검증

```bash
./gradlew :backend:agent:check
```

테스트는 Core 발급 참조의 정확한 전달, 필수 참조 누락 시 Gateway 미호출,
정상/공격 Scenario 변환, 내부 Credential 거부, Gateway Header와 최소 Runtime Body,
`ALLOW/BLOCK` 구분, Timeout/5xx/잘못된 응답의 명시적 실패를 검증합니다.

실제 HTTP 통합 테스트는 정상 Gateway 1회, 누락/null/공백 참조의 Gateway 0회, BLOCK 전달,
4xx/5xx/Timeout/잘못된 응답의 실행 오류 및 재시도 없음을 검증합니다.
Core 생성 실패·트랜잭션 Timeout의 Simulator 미호출 및 AgentRun 상태 갱신은 #74의
검증 책임입니다. Agent는 Core를 호출하지 않으므로 Agent에 Core HTTP Client를 추가하지 않습니다.

현재 Compose 기본 파일의 Agent 서비스는 아직 주석 상태입니다. 위 테스트는 실제 Agent와
HTTP Mock Gateway 사이의 통합 검증이며 전체 Core–Gateway–Mock Finance E2E를 대신하지 않습니다.

## PR 통합 검토 요청

- #59는 Agent의 참조 소비·Gateway 호출·응답 검증에 한정합니다.
- [PR #75](https://github.com/FinGuardName/FinBound/pull/75)의 Core 오케스트레이션을 단일 구현으로
  사용합니다. Core의 실행 실패 상태 처리 및 `scenario` 전달 계약을 확인해 주세요.
- [PR #77](https://github.com/FinGuardName/FinBound/pull/77)의 Gateway 연결 후 실제 응답이 §5의
  `requestId`·`decision`·ALLOW `result`·BLOCK `reasonCodes` 계약을 만족하는지 확인해 주세요.
- 통합 후 Core 발급 → Agent → Gateway → 금융 API의 ALLOW/BLOCK/ERROR E2E를 별도로 검증합니다.
- 관련 기능/인수 조건: `F06`, `F07`, `AC-01`, `AC-14`.
