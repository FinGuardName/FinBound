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
| `backend/core-api` | Employee, Mandate, Case, Passport, Context Resolver, Dashboard API |
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

서비스 구현이 진행되기 전 초기 Compose는 PostgreSQL과 OPA만 기동합니다. 각 서비스가 준비되면 동일 파일의 주석 처리된 통합 지점을 활성화합니다.

## 협업

- 기능 브랜치: `feat/<scope>-<short-name>`
- 수정 브랜치: `fix/<scope>-<short-name>`
- 문서 브랜치: `docs/<short-name>`
- 모든 변경은 Pull Request와 관련 테스트를 포함합니다.
- Contract 변경은 문서를 먼저 수정하고 팀 합의를 거칩니다.

자세한 규칙은 [CONTRIBUTING.md](CONTRIBUTING.md)를 참고하세요.
