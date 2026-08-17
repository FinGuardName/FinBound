# FinGuard 기여 가이드

## 기준 문서

우선순위는 `docs/04-api-contract.md` → `docs/06-common-conventions.md` → 담당 기능 명세 순입니다. Contract를 변경할 때는 먼저 변경을 제안하고 팀 확인 후 문서, 구현, 소비자 테스트를 같은 PR에서 갱신합니다. OpenAPI가 Freeze되는 시점부터 `docs/api-contract.yaml`을 추가해 함께 관리합니다.

## 역할 소유 영역

- Backend 1: `backend/core-api`의 Context, Permission, Core Persistence, Audit/Security Event, Behavior History
- Backend 2: `backend/gateway`, `policy`
- Backend 3: `backend/agent`, `backend/mock-finance`, `backend/audit`, `infrastructure`
- Frontend & AI: `frontend`, `ai-risk`

소유권은 리뷰 라우팅과 핵심 로직의 일관성을 위한 것입니다. Scenario Owner는 E2E 완성을 책임지지만 다른 모듈의 핵심 로직을 독단적으로 구현하지 않고 Module Owner와 Contract로 조율합니다.

## Issue와 Branch

- 기능, 버그, 리팩터링, 테스트, 문서 작업은 Issue에서 시작합니다.
- 하나의 기능 Issue는 하나의 Branch와 PR로 분리합니다.
- Branch 형식은 `{type}/{issue-number}-{short-description}`입니다.
- 예: `feat/12-financial-case`, `fix/27-policy-decision`, `docs/40-api-contract`.
- 같은 Issue의 완료에 필요한 구현, 테스트, Contract, 문서는 한 PR에 포함할 수 있습니다.
- 관련 없는 변경이나 별도 완료 조건을 가진 기능은 새 Issue와 Branch로 분리합니다.

## 커밋

- 커밋은 한 가지 논리적 변경만 포함합니다.
- 형식은 `type: subject`이며, 가능한 경우 기능·테스트·문서 커밋을 나눕니다.
- 예: `feat: Financial Case 생성 기능 추가`.

## Pull Request와 Merge

- PR 제목은 `[TYPE] 작업 요약` 형식을 사용합니다.
- 본문에 `closes #<issue-number>`를 넣어 Issue와 연결합니다.
- 관련 기능/인수 조건 ID를 적습니다.
- 정상, BLOCK, ERROR 흐름 중 영향받는 테스트를 추가합니다.
- 민감 원문이나 Credential이 로그/Audit에 포함되지 않는지 확인합니다.
- Scope 비교를 Rego에 중복 구현하지 않습니다.
- `ALLOW/BLOCK`과 시스템 `ERROR`를 구분합니다.
- Local Test와 적용 가능한 CI를 모두 통과시킵니다.
- 최소 1명의 팀원 Review와 열린 대화의 해결을 확인합니다.
- 확인이 끝난 뒤 Squash and Merge를 기본으로 사용합니다.

검증 범위는 변경 종류에 맞게 적용합니다. 기능 PR은 관련 Unit/Contract/Integration 테스트가 필수이고, 문서 전용 PR은 문서·링크·Contract 정합성 검사를 우선합니다. Coverage 80%와 SonarQube Quality Gate는 해당 CI가 구성된 영역부터 Merge 조건으로 적용합니다.

## 완료 기준

P0 변경은 Unit/Contract/Integration/E2E 중 영향 범위의 테스트를 통과해야 하며, 최종 Release Gate는 Docker Compose 재현입니다.
