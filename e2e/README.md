# Frontend + AI E2E Harness

백엔드 구현을 기다리지 않고 Frontend Real Adapter와 AI Risk 컨테이너를 검증하기 위한 최소 통합 환경입니다.
Stub Core는 DB나 비즈니스 로직을 갖지 않으며, 정해진 요청에 고정 JSON만 반환합니다. 실제 Core API의 대체 구현이나 계약 기준이 아닙니다.

## 실행

Docker가 실행 중인 환경에서 저장소 루트를 기준으로 다음 명령을 실행합니다.

```bash
docker compose -f e2e/docker-compose.frontend-ai.yml up --build --abort-on-container-exit --exit-code-from e2e
```

브라우저에서 직접 확인하려면 테스트가 종료되기 전에 `http://127.0.0.1:18088`을 엽니다. E2E 전용 Operator Credential은 `e2e-operator-credential`입니다. 이 값은 로컬 테스트 전용이며 운영 환경에서 사용하지 않습니다.

종료 후 컨테이너와 네트워크를 정리합니다.

```bash
docker compose -f e2e/docker-compose.frontend-ai.yml down --remove-orphans
```

## 검증 범위

- Frontend가 Real Adapter 모드로 빌드되고 Stub Core를 Nginx reverse proxy로 호출하는지 확인
- AgentRun 생성, 최소 권한 비교, 감사 목록/상세/요약 화면 확인
- AI Risk `/health`, `/ready`, 내부 Credential 인증, risk signal-only 응답 확인
- Credential이 Web Storage, 화면, 정적 JavaScript 번들에 노출되지 않는지 확인
- Stub 응답에 포함한 원문 입력·금융 payload 표식이 화면에 렌더링되지 않는지 확인
- SPA fallback 경로 확인

## 전체 Compose 오버레이

실제 Core API와 함께 Frontend 및 AI 이미지를 띄울 때는 기존 파일을 수정하지 않고 오버레이를 추가합니다.

```bash
docker compose \
  -f infrastructure/docker-compose.yml \
  -f infrastructure/docker-compose.frontend-ai.yml \
  up --build
```

필수 환경변수 `FINGUARD_INTERNAL_CREDENTIAL`을 먼저 설정해야 합니다. Frontend는 기본적으로 `http://core-api:8080`을 사용하며, 필요한 경우 `FRONTEND_CORE_API_UPSTREAM`으로 교체할 수 있습니다.

## 실제 Backend로 전환

이 Harness의 Stub Core는 E2E가 기대하는 최소 응답 형태만 제공합니다. 실제 Backend 통합 시에는 다음 순서로 전환합니다.

1. Frontend 컨테이너의 `CORE_API_UPSTREAM`을 실제 Core API 주소로 지정합니다.
2. 동일한 Playwright 이미지에서 `FRONTEND_BASE_URL`을 실제 Frontend 주소로 지정합니다.
3. 테스트 대상 환경에 유효한 Operator Credential을 런타임 환경변수로 주입합니다.
4. Stub 전용 고정 ID나 건수 대신 실제 시드 데이터에 맞는 별도 시나리오를 사용합니다.

현재 작업 브랜치는 Frontend Real Adapter, AI 이미지, Frontend 이미지 브랜치를 합친 stacked branch입니다. 최종 병합 전 해당 PR들이 `develop`에 반영되면 최신 `develop` 기준으로 재배치합니다.
