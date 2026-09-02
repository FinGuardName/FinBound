# LoanAgent

Backend 3 소유 영역입니다. P0에서는 Spring AI 연동 또는 결정론적 Simulator를 선택할 수 있습니다.

- Core가 발급한 AgentRun을 Case/Passport/Input Reference에 연결해 실행합니다.
- 금융 Tool은 Gateway endpoint만 호출합니다.
- Body의 Agent ID나 권한 목록을 권한 근거로 사용하지 않습니다.
- Mock Financial API를 직접 호출하지 않습니다.

## 컨테이너 실행

이미지는 저장소 루트를 Build Context로 사용합니다. 이미지 빌드에는 Credential을 전달하지
않으며, `Dockerfile.dockerignore`가 개인용 `gradle.properties`, `.env`, 다른 모듈과 빌드
산출물을 Context에서 제외합니다.

```bash
docker build -f backend/agent/Dockerfile -t finguard-agent:local .

export AGENT_SERVICE_CREDENTIAL="$(openssl rand -base64 32)"
export FINGUARD_INTERNAL_CREDENTIAL="$(openssl rand -base64 32)"
docker run --rm --name finguard-agent \
  --memory=512m \
  --read-only \
  --tmpfs /tmp:rw,noexec,nosuid,size=64m \
  --security-opt no-new-privileges:true \
  -p 127.0.0.1:8082:8082 \
  -e AGENT_SERVICE_CREDENTIAL \
  -e FINGUARD_INTERNAL_CREDENTIAL \
  -e GATEWAY_BASE_URL=http://host.docker.internal:8081 \
  finguard-agent:local
```

`AGENT_SERVICE_CREDENTIAL`과 `FINGUARD_INTERNAL_CREDENTIAL`은 Runtime에만 주입합니다.
둘 중 하나가 없으면 설정 검증 단계에서 기동이 실패합니다. `/actuator/health`는 Credential
없이 조회할 수 있지만 `/internal/v1/agent-simulations`는 유효한 Internal Credential이
없으면 `401 INTERNAL_CREDENTIAL_INVALID`로 거부됩니다.

```bash
curl -fsS http://localhost:8082/actuator/health
docker run --rm --entrypoint java finguard-agent:local -version
docker run --rm --entrypoint id finguard-agent:local -u
docker history --no-trunc finguard-agent:local
bash infrastructure/tests/service-container-smoke.sh
```

Runtime은 Java 21, UID/GID `10001`, 컨테이너 메모리 상한의 75% 이하 Heap으로 동작합니다.
위 `docker run` 예시는 512 MiB 상한을 명시합니다. Compose 서비스 연결은 Issue #62에서
담당합니다.

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

## Core Client 경계

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
