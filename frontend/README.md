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
실제 모드에서는 이 Adapter가 AgentRun 생성, Permission Comparison, Public 실행 조회 응답을
조합해 현재 View Model로 변환합니다. 화면이나 Fixture에는 원본 Prompt와 금융 응답 Payload를
포함하지 않습니다.

## Mock / Core API 모드

기본값은 기존 화면 회귀 검증을 위한 `mock`이다. 실제 Core API에 연결할 때는 공개 주소만
환경변수로 지정한다.

```bash
VITE_FINBOUND_API_MODE=real
VITE_FINBOUND_API_BASE_URL=/core-api
VITE_FINBOUND_DEV_PROXY_TARGET=http://localhost:8080
```

개발 서버는 `/core-api`를 로컬 Core로 Proxy하여 브라우저 CORS 우회를 만들지 않는다. Production
Frontend도 같은 경로를 Core API로 Reverse Proxy해야 한다.

`VIEWER_CREDENTIAL`과 `OPERATOR_CREDENTIAL`은 `.env`, Vue 소스, 빌드 산출물 또는 Web
Storage에 넣지 않는다. `real` 모드에서는 화면의 업무 세션 입력란으로 전달하며 새로고침하거나
연결을 종료하면 메모리에서 사라진다.

`src/services/finboundApi.js`는 공식 REST 필드를 현재 View Model로 명시적으로 변환한다.

| Core REST Field | Front View Model |
|---|---|
| `status` | `auditStatus` |
| `promptRiskEvaluationStatus` | `promptEvaluationStatus` |
| `behaviorFeatureVersion` | `featureVersion` |

현재 Core Dashboard 계약은 `severity`와 `riskOnly` 서버 필터를 제공하지 않는다. 실제 모드에서는
두 필터를 비활성화하여 적용되지 않은 조건을 적용된 것처럼 표시하지 않는다. AgentRun 생성 후
Core가 Agent를 호출하며, Vue는 내부 Agent Simulator를 직접 호출하지 않고 아래 Public API를
짧게 Polling하여 실행 상태와 결과를 조회한다.

| Frontend 작업 | Spring Contract |
|---|---|
| 업무 실행 시작 | `POST /api/v1/agent-runs` |
| 현재 업무 권한 축소 근거 | `GET /api/v1/agent-runs/{agentRunId}/permission-comparison` |
| Tool Call 처리 결과 | `GET /api/v1/agent-runs/{agentRunId}/execution` |
| 안전 현황 목록·상세 | `GET /api/v1/audit-events`, `GET /api/v1/audit-events/{auditEventId}` |
| 현황 요약 | `GET /api/v1/dashboard/summary` |

실행 조회 응답은 `agentRunId`, `status(RUNNING/COMPLETED/FAILED)`, `attempts`를 제공해야 한다.
각 Attempt는 `decision(ALLOW/BLOCK)`과 `systemOutcome(COMPLETED/ERROR)`을 분리하고,
`requestId`, `requestedTool`, `targetConsumerId`, `requestedData`, `reasonCodes`,
`downstreamReached`, `responseReleased`, `scopeStatus`만 전달한다. 금융 Payload나 원본 Prompt는
Public 응답에 포함하지 않는다. 완료 응답에 Attempt가 없거나 두 결과 축이 누락되면 Frontend는
정상으로 추정하지 않고 계약 오류로 처리한다.
