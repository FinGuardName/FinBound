# FinGuard 개발 기준 문서

이 폴더에는 실제 개발과 협업에 계속 사용하는 기준 문서만 둔다. 회의록, 변경 이력, 배포용 통합본과 같은 보조 자료는 Git 핵심 문서에서 제외한다.

## 문서 구성

| 문서 | 역할 |
|---|---|
| `00-overview.md` | 문제 정의, Core Invariant, P0/P1 범위와 Runtime 흐름 |
| `01-feature-spec.md` | P0 기능 요구사항과 공통 인수 조건 |
| `02-architecture.md` | Core, Gateway, DB 책임 경계와 시스템 아키텍처 |
| `03-ai-spec.md` | Prompt Risk Snapshot, Behavior AI, 데이터와 평가 정책 |
| `04-api-contract.md` | Core, Gateway, FastAPI, OPA, Audit 간 API/DTO Contract |
| `05-development-guide.md` | Module/Scenario Ownership, 개발 단계와 Definition of Done |
| `06-common-conventions.md` | Enum, Reason Code, 상태값과 공통 규칙 |
| `07-test-scenarios.md` | 핵심 E2E, AI 독립 가치, 인증·장애·우회 검증 시나리오 |
| `08-git-collaboration-convention.md` | Issue, Branch, Commit, PR, Review와 Merge 규칙 |
| `09-team-workflow-quickstart.md` | 위 규칙을 실제로 수행하는 순서와 화면 조작 안내 |

## 문서 우선순위

구현이 충돌할 때는 다음 순서로 확인한다.

```text
04-api-contract.md
→ 06-common-conventions.md
→ 01-feature-spec.md
→ 담당 모듈 문서와 구현
```

## 핵심 불변식

```text
Agent Effective Permission
⊆
Employee Authority
```

- Scope 비교는 Core의 Financial Context Resolver에서만 수행한다.
- OPA는 계산된 `ScopeStatus`를 받아 최종 `ALLOW/BLOCK`을 결정한다.
- AI Risk는 권한을 부여하거나 확대하지 않는다.
- Gateway는 FinGuard PostgreSQL에 직접 접근하지 않는다.
- 원본 Prompt, 금융 Payload, Credential과 Secret을 로그/Audit에 저장하지 않는다.

## OpenAPI 관리

현재는 `04-api-contract.md`를 Contract 기준으로 사용한다. 실제 Endpoint와 DTO가 팀 합의로 Freeze되는 시점에 `api-contract.yaml`을 추가하고, 이후 Markdown Contract와 함께 변경한다.
