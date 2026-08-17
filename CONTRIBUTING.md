# FinGuard 기여 가이드

## 기준 문서

우선순위는 `docs/04-api-contract.md` → `docs/06-common-conventions.md` → 담당 기능 명세 순입니다. Contract를 변경할 때는 관련 문서, 구현, 테스트를 한 PR에 함께 반영합니다.

## 역할 소유 영역

- Backend 1: `backend/core-api`
- Backend 2: `backend/gateway`, `policy`
- Backend 3: `backend/agent`, `backend/mock-finance`, `backend/audit`, `infrastructure`
- Frontend & AI: `frontend`, `ai-risk`

소유권은 리뷰 라우팅을 위한 것이며 다른 영역의 기여를 제한하지 않습니다.

## 브랜치와 커밋

- 브랜치는 `feat/`, `fix/`, `test/`, `docs/`, `chore/` 중 하나로 시작합니다.
- 커밋은 한 가지 논리적 변경만 포함합니다.
- 권장 커밋 형식: `type(scope): summary` (예: `feat(gateway): verify agent credential`)

## Pull Request 체크리스트

- 관련 기능/인수 조건 ID를 적습니다.
- 정상, BLOCK, ERROR 흐름 중 영향받는 테스트를 추가합니다.
- 민감 원문이나 Credential이 로그/Audit에 포함되지 않는지 확인합니다.
- Scope 비교를 Rego에 중복 구현하지 않습니다.
- `ALLOW/BLOCK`과 시스템 `ERROR`를 구분합니다.

## 완료 기준

P0 변경은 Unit/Contract/Integration/E2E 중 영향 범위의 테스트를 통과해야 하며, 최종 Release Gate는 Docker Compose 재현입니다.
