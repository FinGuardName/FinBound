# LoanAgent

Backend 3 소유 영역입니다. P0에서는 Spring AI 연동 또는 결정론적 Simulator를 선택할 수 있습니다.

- Core가 발급한 AgentRun을 Case/Passport/Input Reference에 연결해 실행합니다.
- 금융 Tool은 Gateway endpoint만 호출합니다.
- Body의 Agent ID나 권한 목록을 권한 근거로 사용하지 않습니다.
- Mock Financial API를 직접 호출하지 않습니다.

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

검증:

```bash
./gradlew :backend:agent:check
```
