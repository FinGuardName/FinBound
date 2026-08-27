# Frontend

Frontend & AI 담당의 Vue 3 애플리케이션입니다. P0 화면은 은행 직원의 일반적인 대출심사 흐름에 AI 업무 도우미를 결합한 화면과, 임직원이 이해하기 쉬운 AI 업무 안전 현황으로 구성합니다. Employee Authority와 Agent Effective Permission 비교 근거는 별도 메뉴로 분리하지 않고 대출심사 화면의 AI 업무 보호 설정에서 함께 제공합니다.

기본 화면에는 신청 정보, 자료 확인, 처리 결과와 다음 업무를 일상적인 은행 업무 용어로 표시합니다. Request ID, Reason Code, Tool 같은 기술 정보는 보안 처리 내역에 접어 두어 필요한 사용자만 확인할 수 있습니다.

P0의 `LOAN_REVIEW` 계약과 신용·소득·부채 조회 Tool 범위를 유지하면서 신규 대출 심사, 대출 한도 재심사, 심사서류 보완 확인의 세 가지 업무 시뮬레이션을 제공합니다. 직원은 자료 범위나 권한을 고르지 않고 현재 수행할 은행 업무만 선택합니다. 시스템은 `Employee Authority ∩ Permission Template ∩ Financial Case ∩ Consumer Mandate`로 AI의 최소 권한을 자동 산정합니다.

한 번의 업무 실행 안에서 필요한 자료 조회는 계속 처리하고, Agent가 시도한 다른 고객·가족·동의 만료 자료 접근만 금융시스템 호출 전에 개별 차단합니다. 실제로 범위를 넓혀야 하는 업무는 단순 선택 항목이 아니라 별도 Financial Case와 TaskPassport를 발급받아야 합니다.

```bash
cd frontend
pnpm install --frozen-lockfile
pnpm dev
pnpm test
```

Frontend는 Spring Core API만 호출하며 PostgreSQL에 직접 연결하지 않습니다. AI 업무 지원 화면은
AgentRun 생성 Command API를 사용하고, 권한 비교와 Dashboard는 Spring Read-only API를 사용합니다.

현재 P0 Mock에서는 `src/services/finboundApi.js` 뒤에 가상 데이터를 둡니다. 목록의 검색 조건과
페이지 정보도 이 API 경계로 전달해 브라우저가 전체 감사 기록을 무제한으로 적재하지 않도록 합니다.
Spring API가 준비되면 이 Adapter가 AgentRun 생성, Permission Comparison, AgentRun별 AuditEvent를
조합해 현재 View Model로 변환합니다. 화면이나 Fixture에는 원본 Prompt와 금융 응답 Payload를
포함하지 않습니다.

| Frontend 작업 | Spring Contract |
|---|---|
| 업무 실행 시작 | `POST /api/v1/agent-runs` |
| 현재 업무 권한 축소 근거 | `GET /api/v1/agent-runs/{agentRunId}/permission-comparison` |
| Tool Call 처리 결과 | AgentRun의 `ToolCallAttempt` + `ExecutionOutcome` |
| 안전 현황 목록·상세 | `GET /api/v1/audit-events`, `GET /api/v1/audit-events/{auditEventId}` |
| 현황 요약 | `GET /api/v1/dashboard/summary` |
