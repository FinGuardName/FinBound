# LoanAgent

Backend 3 소유 영역입니다. P0에서는 Spring AI 연동 또는 결정론적 Simulator를 선택할 수 있습니다.

- AgentRun을 Case/Passport/Input Reference에 연결합니다.
- 금융 Tool은 Gateway endpoint만 호출합니다.
- Body의 Agent ID나 권한 목록을 권한 근거로 사용하지 않습니다.
- Mock Financial API를 직접 호출하지 않습니다.

## AgentRun Skeleton

```http
POST /api/v1/agent-runs
Content-Type: application/json
```

```json
{
  "employeeId": "EMP-101",
  "consumerId": "CUST-1001",
  "taskType": "LOAN_REVIEW",
  "inputText": "CUST-1001의 대출심사를 진행해줘."
}
```

성공하면 AgentRun은 `CREATED → RUNNING`으로 전환됩니다. 이후 실행 주체가
`COMPLETED | FAILED`로만 전환할 수 있으며 완료 상태에서 다시 시작할 수 없습니다.

입력 원문은 `SecuredInputStore` 뒤에 두고 AgentRun 응답에는 `inputRefs`만 포함합니다.
`inputHash`는 동일 입력 Snapshot 재사용을 위한 내부 참조 결과이며 응답·Audit에 원문과 함께
노출하지 않습니다.

### 합의 필요

현재 `LocalAgentRunContextProvider`는 독립 실행용으로 `LOCAL-CASE-*`, `LOCAL-PASS-*` 참조만
발급합니다. 이 값은 권한 근거가 아니며 Scope나 Effective Permission을 계산하지 않습니다.
Backend1의 Case/Passport 생성 Contract가 연결되면 Core Client 구현으로 교체해야 합니다.

현재 `InMemorySecuredInputStore`와 `InMemoryAgentRunRepository`는 재시작 시 사라지는 Skeleton입니다.
통제된 영속 입력 저장소와 AgentRun Persistence의 최종 소유권은 Backend1과 합의해야 합니다.

검증:

```bash
./gradlew :backend:agent:check
```
