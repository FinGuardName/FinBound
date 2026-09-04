# Infrastructure — P0 Compose

Issue #62는 Backend 3 서비스 배선입니다. 정상 금융 호출 경로는
`Agent → Gateway → Mock Finance`이며 Scope 비교·Persistence는 Core의 책임입니다.
Kubernetes는 P1입니다.

## 선행 PR과 작업 경계

| PR | 필요한 구현 | #62와의 관계 |
|---|---|---|
| [#83](https://github.com/FinGuardName/FinBound/pull/83) / #61 | Agent·Mock Finance Dockerfile | 필수 빌드 입력; #62에서 복제하지 않음 |
| [#77](https://github.com/FinGuardName/FinBound/pull/77) | Gateway 실제 Core/AI/Downstream Client | 기본은 `real-core,real-downstream`; AI override에서 `real-ai` 활성화 |
| [#75](https://github.com/FinGuardName/FinBound/pull/75) | Core → Agent 실행 | `AGENT_URL=http://agent:8082` 연결 |
| [#78](https://github.com/FinGuardName/FinBound/pull/78), [#79](https://github.com/FinGuardName/FinBound/pull/79) | Agent 발급 참조·공격 Scenario | 업무 E2E 선행 작업 |
| [#56](https://github.com/FinGuardName/FinBound/pull/56) | Core Audit/Dashboard | Gateway Audit 계약 통합 확인 |
| [#93](https://github.com/FinGuardName/FinBound/pull/93), #72 | AI 이미지·Frontend/AI Compose override·실제 E2E | `docker-compose.frontend-ai.yml`에서 통합 |

이 PR들이 미병합인 checkout에서 #62 파일만으로 전체 업무 E2E가 완성되지는 않습니다.
특히 #83 미반영 시 Dockerfile이 없어 빌드할 수 없습니다. 선행 코드를 자동 병합하거나
Mock으로 대체하는 스크립트는 제공하지 않습니다.

## 실행

Docker Desktop Linux containers / Docker Compose v2.30 이상, 테스트에는 PowerShell 7이 필요합니다.
Compose는 `infrastructure/.env`를 읽습니다. 기존 파일은 덮어쓰지 않습니다.

```powershell
cd infrastructure
if (!(Test-Path .env)) { Copy-Item ../.env.example .env }
# .env의 Credential과 POSTGRES_PASSWORD를 서로 다른 무작위 값으로 채웁니다.
docker compose config --services
docker compose up -d --build
docker compose ps
```

필수 값: `POSTGRES_PASSWORD`, `AGENT_SERVICE_CREDENTIAL`, `FINGUARD_INTERNAL_CREDENTIAL`,
`VIEWER_CREDENTIAL`, `OPERATOR_CREDENTIAL`, `OPERATOR_EMPLOYEE_ID`.
Credential은 각각 32바이트 이상의 무작위 값으로 생성하고 Viewer와 Operator는 서로 다르게 설정합니다.
DB 비밀번호도 빈 기본값 대신 직접 생성합니다. 값은 명령행 인수·스크린샷·PR·CI 로그에
붙이지 않습니다. 실제 `.env`는 커밋하지 않습니다. 기존 DB 볼륨의 비밀번호는 환경변수를 바꿔도
갱신되지 않습니다.

기본 서비스는 PostgreSQL, Core, OPA, Gateway, Agent, Mock Finance의 6개입니다.
실제 AI와 Frontend를 포함한 8개 서비스는 데모 시드와 전용 override를 함께 사용합니다.

```powershell
docker compose -f docker-compose.yml -f docker-compose.demo.yml -f docker-compose.frontend-ai.yml up -d --build --wait
```

기본 6개 서비스 구성은 AI 서비스가 없으므로 `real-core,real-downstream`만 활성화하고,
AI Risk 신호는 결정론적 Mock을 사용합니다. Mock AI는 권한 판정을 하지 않으며
Core Context와 OPA 정책 집행을 우회하지 않습니다. #72 override가 AI를 추가할 때
`real-ai`와 AI readiness 의존성을 함께 활성화합니다.

### 선택적 override

| 목적 | 기본 `-f docker-compose.yml` 뒤에 추가 |
|---|---|
| 데모 시드 (기본은 시드 없음) | `-f docker-compose.demo.yml` |
| 브라우저에서 Core 접근 | `-f docker-compose.expose.yml` |
| 실제 AI + Frontend | `-f docker-compose.demo.yml -f docker-compose.frontend-ai.yml` |

데모 시드는 영속 볼륨에 남습니다. 일반 `docker compose down`은 DB 볼륨을 보존합니다.
`down -v`는 DB를 삭제하므로 명시적으로 재설정이 필요할 때만 사용합니다.
스키마는 Flyway 소유이며 `ddl-auto=validate`, 데모 시드는 `local` 프로파일 전용입니다.

## Credential 전달과 로그

호스트 환경변수/`.env` → Compose `secrets.environment` → `/run/secrets/<target>` →
`docker/with-secrets.sh` → 기존 애플리케이션 환경변수 순서로 전달합니다.
서비스별 필요한 secret만 마운트합니다.

| 소비 서비스 | 애플리케이션에 전달하는 값 |
|---|---|
| Agent | `AGENT_SERVICE_CREDENTIAL`, `FINGUARD_INTERNAL_CREDENTIAL` |
| Mock Finance | `FINGUARD_INTERNAL_CREDENTIAL` |
| Gateway | `FINGUARD_CREDENTIALS_VALIDAGENTTOKENS`, `FINGUARD_CREDENTIALS_INTERNALSERVICE` |
| Core | `POSTGRES_PASSWORD`, `FINGUARD_INTERNAL_CREDENTIAL`, `FINGUARD_API_VIEWERCREDENTIAL`, `FINGUARD_API_OPERATORCREDENTIAL` |
| AI Risk | `FINGUARD_INTERNAL_CREDENTIAL` |
| PostgreSQL | 공식 이미지의 `POSTGRES_PASSWORD_FILE` |

Gateway의 두 설정은 #77의 `finguard.credentials.*`를 덮어씁니다.
개발용 고정 토큰/default Credential을 Compose에서 사용하지 않습니다.
Core API 인증은 `finguard.api.*`에 바인딩합니다. Agent에 Operator/Viewer/DB Credential을 주지 않습니다.

Secret 누락은 Compose 컨테이너 생성 또는 실행 스크립트에서 실패합니다.
빈 값·공백만 있는 값·32자 미만의 값·동일한 Viewer/Operator Credential은 JVM 실행 전에
일반 오류와 exit 64로 거부합니다.
`config`는 배포가 아니므로 secret 존재 여부까지 보장하지 않습니다.

기본 Compose의 전체 `config`/컨테이너 설정에는 secret **이름**만 남고 값은 남지 않습니다.
구조 확인에는 `docker compose config --services` 또는 `config -q`를 권장합니다.
`config --environment`, 컨테이너의 `env`/`printenv`, shell tracing, 프로세스 환경 덤프는 금지합니다.
다른 override가 환경변수에 secret 값을 직접 치환하면 전체 config는 다시 민감해집니다
전용 계약 테스트는 AI override를 합친 config에도 값이 들어가지 않는지 검사합니다.

Compose secret은 Docker/호스트 관리자에게서 비밀을 숨기는 경계가 아닙니다.
관리자는 런타임 환경·secret 파일을 읽을 수 있습니다.
참고: [Docker Compose secrets](https://docs.docker.com/compose/how-tos/use-secrets/).

## 빌드 및 readiness

Agent/Mock Finance는 #61 Dockerfile을 재사용합니다. Core/Gateway는 infrastructure의 공통
Compose 전용 Multi-stage Dockerfile을 사용합니다. root Runtime과 `COPY . .`을 사용하던
기존 `backend/gateway/Dockerfile`은 제거해 Gateway 이미지 빌드 경로를 하나로 유지합니다.
Allowlist build context는 `.env`, 개인 `gradle.properties`, Git 정보, 로컬 빌드 결과를 제외합니다.
따라서 개인 Windows JDK 경로가 Linux 이미지로 복사되지 않습니다.
Runtime은 JRE 21, non-root, `/tmp` tmpfs, 메모리 상한을 사용합니다.
모든 기본 서비스는 `restart: unless-stopped`로 예상치 못한 종료 후 재기동합니다.
Compose의 environment-backed secret은 컨테이너 생성 시 파일로 전달되므로 Java 서비스에는
`read_only`를 사용하지 않습니다(Compose가 이 조합을 거부함). Read-only root가 필요하면
file-backed secret 방식으로 전환하는 별도 운영 구성이 필요합니다. OPA는 read-only입니다.

기본 기동 순서: PostgreSQL healthy → Core healthy; OPA/Mock Finance healthy → Gateway healthy → Agent healthy.
8개 구성에서는 AI Risk healthy → Core/Gateway healthy → Agent/Frontend healthy 조건이 추가됩니다.
Core → Agent는 요청 시 호출 관계이며 기동 의존성으로 역방향 연결하지 않아 순환을 피합니다.
기본 Agent→Gateway timeout은 10초, Core→Agent는 15초입니다. Timeout을 바꾸면
바깥 호출의 대기 시간이 전체 내부 호출보다 짧아지지 않도록 함께 확인합니다.
Spring은 `/actuator/health`, OPA는 실행 중인 서버의 `/health?bundles=true`를 검사합니다.
Health 성공은 업무 권한·AI 준비·Audit 계약의 E2E 성공을 뜻하지 않습니다.

## 네트워크와 주소

| Zone | 소속 서비스 |
|---|---|
| public-zone | Gateway (Frontend는 전용 override에서 추가) |
| internal-zone | Core, OPA, Gateway, Agent (AI와 Frontend는 전용 override에서 추가) |
| finance-zone (`internal: true`) | Gateway, Mock Finance만 |
| data-zone (`internal: true`) | PostgreSQL, Core만 |

PostgreSQL은 호스트 포트를 publish하지 않습니다. Gateway/Agent/Mock Finance에는 DB 환경변수,
DB secret, data-zone 연결이 없습니다. 기본 호스트 진입점은 `127.0.0.1:8091 → Gateway:8081`뿐입니다.
Core expose override도 `127.0.0.1:8080`으로만 엽니다. Agent/Mock Finance는 호스트에 열지 않습니다.

- Gateway → Mock Finance: `http://mock-finance:8083`
- Agent → Gateway: `http://gateway:8081`
- Agent → Core: `http://core-api:8080` (`CORE_API_BASE_URL`; #78 이후 직접 생성 Client는 제거됨)
- Core → Agent: `http://agent:8082`
- Core/Gateway → AI Risk: `http://ai-risk:8000`
- Frontend → Core Public API: `http://core-api:8080` (Nginx `/core-api/api/v1/**` allowlist)

Agent와 Mock Finance는 공유 네트워크가 없습니다. Agent는 Mock Finance의 서비스 DNS나
finance-zone 직접 IP로 접근할 수 없고, Gateway만 finance-zone에 참여해 금융 Tool을 호출합니다.

## 자동 검증 (저장소 루트)

```powershell
pwsh -File infrastructure/tests/compose-contract.ps1
pwsh -File infrastructure/tests/secret-entrypoint.ps1
pwsh -File infrastructure/tests/compose-smoke.ps1
pwsh -File infrastructure/tests/frontend-ai-e2e.ps1
./gradlew.bat :backend:agent:check :backend:mock-finance:check :backend:audit:check
./gradlew.bat check
```

`compose-contract.ps1`: 엔진 없이 전체 config를 메모리로 캡처해 secret canary 비노출,
서비스·포트·URL·Credential 매핑·실제 Gateway 프로파일·health 의존성·데모/expose 조합을 검사합니다.
기존 프로세스 환경변수는 종료 시 복원합니다.

`secret-entrypoint.ps1`: 네트워크 없는 컨테이너에서 실행 스크립트의 정상 전달, 누락, 빈 값,
공백, 32자 미만, 일부 누락, 잘못된 변수명·경로, 동일 Viewer/Operator를 검사합니다.

`compose-smoke.ps1`: 고유한 `finguard-62-<random>` 프로젝트와 무작위 테스트 Credential로
실제 build/up, 6개 health, 서비스 간 HTTP, Core→DB 양성 대조,
Agent/Gateway/Mock Finance→DB DNS 및 직접 IP 접근 불가, Internal Credential 누락·불일치 401,
Agent→Mock Finance DNS·직접 IP 접근 불가, Credential 없는 기동 실패·후속 명령 미실행,
로그·컨테이너 metadata 비노출를 검사합니다.
종료 시 **자신이 만든 테스트 프로젝트와 테스트 DB 볼륨만** 삭제합니다.
선행 PR 소스는 별도 디렉터리로 준비해 `-RepositoryRoot`로 지정할 수 있습니다.

추가 양성 대조로 Gateway Credential의 실제 Mock Finance Tool 호출 성공과
Agent→Gateway의 존재하지 않는 Run에 대한 fail-closed BLOCK을 확인합니다.
금융 응답은 출력하지 않습니다. 이 대조는 정책 ALLOW E2E와 구분합니다.

전용 CI는 infrastructure뿐 아니라 Backend·Policy·Gradle 빌드 입력 변경에도 실행됩니다.
설정 계약·Credential 기동 테스트와 실제 이미지 빌드·6개 서비스 기동 스모크를
PR·`main`·`develop` Push에서 자동 실행하며, 수동 실행은 `full_stack`을 선택한 경우에만 실행합니다.

`frontend-ai-e2e.ps1`는 고유한 `finguard-72-<random>` 프로젝트에서 8개 서비스를 빌드하고,
실제 브라우저 연결과 Core→Agent→Gateway→AI→OPA→Mock Finance 흐름을 검사합니다.
정상 ALLOW, 범위 BLOCK, Prompt Risk BLOCK, AI 중단 fail-closed와 downstream 미도달,
브라우저 저장소·정적 번들·서비스 로그의 Credential/원문/금융 응답 비노출을 검증합니다.
테스트 Credential은 매번 무작위로 만들며 종료 시 해당 프로젝트와 DB 볼륨만 삭제합니다.

## 업무 E2E 범위

외부 API/DTO/Scope/Policy Contract는 변경하지 않습니다. Frontend는 문서화된
`GET /api/v1/audit-events`의 최신 결과에서 `agentRunId`가 같은 기록을 찾아 실행 완료를 표시합니다.
AI는 위험 신호만 반환하며 최종 ALLOW/BLOCK은 계속 OPA가 결정합니다.

AI 모델 artifact digest·누락 시 readiness 실패는 `ai-risk` 단위/컨테이너 테스트와
`AI Risk container image` CI가 담당하고, 이 E2E는 검증된 artifact로 `/ready` 성공을 확인합니다.
기능/인수 조건 ID: **F07, F18, F21, AC-01, AC-02, AC-14, AC-16, AC-19**.
