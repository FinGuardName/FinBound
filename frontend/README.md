# Frontend

Frontend & AI 담당의 Vue 3 애플리케이션입니다. P0 화면은 LoanAgent 실행, 권한 비교, Security Dashboard 세 개로 제한합니다.

```bash
cd frontend
npm install
npm run dev
npm run test
```

Frontend는 Spring Read-only API만 호출하며 PostgreSQL에 직접 연결하지 않습니다.

현재 Phase 1에서는 `src/services/finguardApi.js` 뒤에 가상 데이터를 둡니다. Spring API가 준비되면
View를 변경하지 않고 이 API 계층의 구현만 교체합니다. 화면이나 Fixture에는 원본 Prompt와 금융
응답 Payload를 포함하지 않습니다.
