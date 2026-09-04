# FinGuard

FinGuard는 금융기관 직원의 넓은 업무 권한을 AI Agent에 그대로 상속하지 않고, 현재 Financial Case와 Consumer Mandate에 맞는 최소 권한만 Task Passport로 발급한 뒤 Tool Call 직전에 정책을 집행하는 Runtime Authorization Gateway입니다.

## Core invariant

```text
Agent Effective Permission ⊆ Employee Authority
```

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

## 저장소 구조

| 경로 | 책임 |
|---|---|
| `backend/core-api` | Context, Permission, Audit/Security Persistence, Behavior History, Dashboard API |
| `backend/gateway` | Identity, AuthorizationContext, OPA 연동, Enforcement, Idempotency |
| `backend/agent` | AgentRun과 LoanAgent/Simulator |
| `backend/mock-finance` | 가상 금융 API와 내부 Credential 검증 |
| `backend/audit` | 공통 Audit 계약과 구현 가이드 |
| `ai-risk` | Prompt Injection과 Behavior Anomaly Risk Engine |
| `frontend` | Vue 3 실행·권한 비교·Security Dashboard |
| `policy` | OPA/Rego 정책과 테스트 |
| `contracts` | 서비스 간 계약의 기준점 |
| `infrastructure` | Docker Compose(P0), Kubernetes(P1) |
| `docs` | 최종 MVP 기준 문서 |

## 개발 순서

1. `docs/04-api-contract.md`, `docs/06-common-conventions.md`, `contracts/README.md`를 먼저 확인합니다.
2. 독립 Mock을 만든 뒤 Case-aware Authorization을 AI 없이 완성합니다.
3. Gateway/Identity/Audit, Prompt Risk, Behavior Risk, Frontend 순으로 통합합니다.
4. P0는 Docker Compose E2E를 Release Gate로 사용합니다. Kubernetes는 P1입니다.

## 시작하기

```bash
cp .env.example .env
docker compose -f infrastructure/docker-compose.yml up -d
```

기본 Compose는 PostgreSQL, Core API, OPA, Gateway, LoanAgent, Mock Finance의 6개 서비스를 기동합니다.
Frontend와 AI Risk까지 포함한 8개 서비스 검증은 `infrastructure/tests/frontend-ai-e2e.ps1`을 실행합니다.

## 협업

- 모든 기능 작업은 Issue에서 시작합니다.
- Branch 형식: `{type}/{issue-number}-{short-description}`
- 하나의 기능 Issue는 하나의 Branch와 PR로 분리합니다.
- 같은 기능의 구현·테스트·필수 Contract/문서 변경은 한 PR에 함께 포함할 수 있습니다.
- Local Test, CI, 관련 Contract, 팀원 Review를 모두 확인한 뒤 Merge합니다.
- `main`에는 직접 Push하지 않습니다.
- Contract 변경은 문서를 먼저 수정하고 팀 합의를 거칩니다.

자세한 규칙은 [CONTRIBUTING.md](CONTRIBUTING.md)와 [Git 협업 규칙](docs/08-git-collaboration-convention.md)을 참고하세요.
