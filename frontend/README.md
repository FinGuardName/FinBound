# Frontend

Frontend & AI 담당의 Vue 3 애플리케이션입니다. P0 화면은 은행 대출 업무 안에서 동작하는 LoanAgent 실행 화면과 Security Dashboard로 구성합니다. Employee Authority와 Agent Effective Permission 비교 근거는 별도 메뉴로 분리하지 않고 Agent 실행 화면의 FinGuard 권한 경계에서 함께 제공합니다.

```bash
cd frontend
pnpm install --frozen-lockfile
pnpm dev
pnpm test
```

Frontend는 Spring Read-only API만 호출하며 PostgreSQL에 직접 연결하지 않습니다.

현재 Phase 1에서는 `src/services/finguardApi.js` 뒤에 가상 데이터를 둡니다. Spring API가 준비되면
View를 변경하지 않고 이 API 계층의 구현만 교체합니다. 화면이나 Fixture에는 원본 Prompt와 금융
응답 Payload를 포함하지 않습니다.
