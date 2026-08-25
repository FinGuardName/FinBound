# Frontend

Frontend & AI 담당의 Vue 3 애플리케이션입니다. P0 화면은 은행 직원의 일반적인 대출심사 흐름에 AI 업무 도우미를 결합한 화면과, 임직원이 이해하기 쉬운 AI 업무 안전 현황으로 구성합니다. Employee Authority와 Agent Effective Permission 비교 근거는 별도 메뉴로 분리하지 않고 대출심사 화면의 AI 업무 보호 설정에서 함께 제공합니다.

기본 화면에는 신청 정보, 자료 확인, 처리 결과와 다음 업무를 일상적인 은행 업무 용어로 표시합니다. Request ID, Reason Code, Tool 같은 기술 정보는 보안 처리 내역에 접어 두어 필요한 사용자만 확인할 수 있습니다.

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
