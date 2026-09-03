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

Agent Simulator의 7개 Scenario는 PR #79 소비자 리뷰를 반영한
`docs/04-api-contract.md` §3.1을 따릅니다. Simulator는 모든 Scenario를 다음
Gateway Contract로 변환합니다 (정상 신용점수 예시). 현재 Core Enum은 기존
2개 Scenario만 지원하므로 확장 Scenario를 Core 실행 경로에서 사용하려면
같은 7개 값으로 확장해야 합니다.

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

최종 실행 방향은 **Core → Agent → Gateway**입니다. `POST /api/v1/agent-runs`와
다음 데이터의 발급·저장 책임은 Core에 있습니다.

- Financial Case
- Task Passport
- Secured Input / PromptRiskSnapshot
- AgentRun ID와 상태

P0 Simulator는 Core가 발급한 `agentRunId`와 `passportId`를 요청으로 받습니다.
Agent는 이 참조를 로컬에서 생성하거나 저장하지 않고, AgentRun 상태를 전환하거나
입력 원문을 보관하지 않습니다.

다만 Agent는 참조가 비어 있지 않은지만 검사하며, 실제 발급 여부나 Run/Passport 결합을
증명하지 않습니다. 공유 내부 Credential도 Core만의 신원 증명은 아닙니다. non-blank 미발급
참조는 Gateway로 전달될 수 있고, Gateway 인증 후 실제 Core Resolver에서 거부해야 합니다.
현재 Gateway의 기본 MockCoreClient는 이를 보장하지 않으므로 보안 검증이나 실제 데이터 실행에
사용하면 안 됩니다. 실제 Core 연동 및 기본 fail-closed 설정은 통합 환경에서 확인합니다.

실행 흐름:

```text
Operator
→ Core POST /api/v1/agent-runs
→ Core가 Case / Passport / Input Reference / AgentRun 발급
→ Core가 발급 agentRunId / passportId로 Simulator 호출
→ Agent가 동일 참조로 Gateway Tool Call 실행
```

Core 생성 실패·Timeout 시 Simulator를 호출하지 않는 오케스트레이션과 상태 갱신은
Core의 책임입니다. Agent에는 중복 Core Client·Controller 연결·상태 갱신을 두지 않으며,
Agent는 Operator Credential을 보유하거나 Core 생성 API를 직접 호출하지 않습니다.

Case/Input Reference는 Core의 AgentRun에 연결되어 있으며 P0 Simulator Body에는 전달하지
않습니다. 입력 원문을 Agent가 저장하거나 재전송하지도 않습니다. Simulator 호출 후 Timeout은
이미 실행된 금융 요청의 취소를 의미하지 않으므로 자동 재시도하지 않습니다.

Gateway의 필수 응답 필드 누락, 잘못된 JSON, `403 + ALLOW`, 금융 결과가 포함된 BLOCK은
`GATEWAY_RESPONSE_INVALID`로 실패합니다. Gateway 시스템 오류는 HTTP 502와 실행 오류 코드로
반환하고 `ALLOW/BLOCK`으로 바꾸지 않으며, 원본 오류 본문을 노출하지 않습니다.
ALLOW에는 요청과 같은 `result.tool`, `result.consumerId`, Tool별 숫자 결과가 필요합니다.
`{"tool":"CREDIT_SCORE_READ"}`만 있는 stub 응답은 성공으로 받지 않습니다.
요청과 응답을 대조하는 이 검사는 Scope 판단이나 참조 출처 검증을 대신하지 않습니다.

Simulator의 ALLOW/BLOCK 응답은 HTTP 200 `{scenario, gatewayResponse}`이며 정책 결과는
`gatewayResponse.decision`에 있습니다. 실행 오류는 HTTP 502 `{errorCode, message}`입니다.
전체 응답 예시는 `docs/04-api-contract.md` §3.1을 참조하세요.

현재 `develop`에는 #77·#75·#78이 반영되어 세 Tool/Data, Core → Agent 호출,
엄격한 Gateway 응답 검증을 제공합니다. 다만 Core Enum은 아직 기존 2개
Scenario만 수용하므로 #79 병합 전 7개 값으로 맞춰야 합니다.

## 검증

```bash
./gradlew :backend:agent:check
```

테스트는 전달받은 참조의 변경 없는 전달, 필수 참조 누락 시 Gateway 미호출,
정상/공격 Scenario 변환, 내부 Credential 거부, Gateway Header와 최소 Runtime Body,
`ALLOW/BLOCK` 구분, Timeout/5xx/잘못된 응답의 명시적 실패를 검증합니다.

실제 HTTP 통합 테스트는 정상 Gateway 1회, 누락/null/공백 참조의 Gateway 0회, BLOCK 전달,
4xx/5xx/Timeout/잘못된 응답의 실행 오류 및 재시도 없음을 검증합니다.
Core 생성 실패·트랜잭션 Timeout의 Simulator 미호출 및 AgentRun 상태 갱신은 Core의
검증 책임입니다. Agent는 Core를 호출하지 않으므로 Agent에 Core HTTP Client를 추가하지 않습니다.

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
Mandate 기준 Fixture는 **발급 전에** ACTIVE Mandate를 `[CREDIT_SCORE, INCOME]`으로 제한하고,
Authority/Template은 세 Tool/Data를 허용한 채 새 Passport를 발급합니다. 현재 Calculator는
DEBT와 DEBT_READ를 모두 제외합니다. 다른 Scope/Risk/Limit이 정상이고 버전이 일치하면
기대 전체 코드는 `[DATA_SCOPE_VIOLATION, MANDATE_SCOPE_VIOLATION, TOOL_SCOPE_VIOLATION]`입니다.
**발급 후** Mandate의 DEBT를 제거하고 버전을 올리는 별도 Fixture는 기존 Passport 권한을
유지하므로 `[MANDATE_SCOPE_VIOLATION, TASK_PASSPORT_INACTIVE]`를 기대합니다.
현재 Rego는 버전 불일치를 STALE로 세분화하지 않습니다. 실제 조합은 Core/Gateway 통합에서
검증해야 하며, Agent는 반환된 코드를 추가·제거·정렬하지 않습니다.
일반 Seed가 세 Tool/Data를 모두 허용하면 공격 Scenario도 ALLOW가 될 수 있으며,
Agent는 이를 임의로 BLOCK으로 바꾸지 않습니다. NORMAL_INCOME/TOOL_SCOPE_ATTACK과
NORMAL_DEBT/MANDATE_SCOPE_ATTACK의 요청은 각각 같고, 사용하는 서버 Context가 다릅니다.

`AgentScenarioMappingTest`는 7개 Scenario의 반복 가능한 최소 Body 변환, 응답 그대로 전달,
ERROR 유지, 설정 불변성을 검증합니다. `AgentAttackScenarioIntegrationTest`는 실제 Agent HTTP와
Mock Gateway로 정상 3개/공격 4개의 호출 1회 및 BLOCK/Reason Code 전달을 검증합니다.
단일 Scope Reason 전달과 복수 코드의 순서·중복 보존은 별도 테스트로 구분합니다.
`policy/finguard_authz_test.rego`는 주어진 ScopeStatus의 단일/복합 위반이 정확한 전체
Reason Code로 변환되는지 검증합니다. 이는 Core가 해당 Scope를 생성했다는 E2E 증거는 아닙니다.
Mock은 Scope나 OPA를 재구현하지 않습니다. 실제 금융 downstream 0회 검증은 전용 Core Fixture
준비 후 필요하며, 이 테스트를 전체 인가 E2E로 간주하지 않습니다.

현재 Compose 기본 파일의 Agent 서비스는 아직 주석 상태입니다. 위 테스트는 실제 Agent와
HTTP Mock Gateway 사이의 통합 검증이며 전체 Core–Gateway–Mock Finance E2E를 대신하지 않습니다.
미발급 참조에 대한 테스트도 Mock이 반환한 거부 결과의 전달을 검증할 뿐, 실제 Core 조회나
기본 Gateway 프로파일의 보안을 입증하지 않습니다.

## 통합 검토 사항

- Core의 `AgentSimulationScenario`를 Agent와 같은 7개 값으로 확장합니다.
- 전용 Core Fixture로 실제 Scope 위반·전체 Reason Code·금융 downstream 0회를
  검증합니다.
- Core 발급 → Agent → Gateway → 금융 API의 ALLOW/BLOCK/ERROR E2E를 별도로 검증합니다.
- 정상 HTTP Fixture에도 `result.tool`·`consumerId`·Tool별 금융 값을 포함합니다.
- 관련 기능/인수 조건: `F06`, `F07`, `AC-01`, `AC-14`.
