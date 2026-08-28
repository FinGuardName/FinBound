# Infrastructure

P0는 Docker Compose, P1은 Kubernetes hardening입니다. 현재 Compose는 PostgreSQL, OPA, Core API를
제공합니다. Gateway, Agent, Mock Finance, AI Risk, Frontend는 각 Dockerfile이 추가되면 연결합니다.

## 실행

Compose는 **compose 파일이 있는 디렉터리**의 `.env`를 읽습니다. `.env.example`은 저장소 루트에
있으므로 `infrastructure/.env`로 복사해야 합니다.

```bash
cd infrastructure
cp ../.env.example .env               # 최초 1회
docker compose up -d --build          # core-api 포트는 열리지 않습니다
```

`.env`의 자격 증명 값은 비어 있습니다. 각자 생성해서 채우세요(`openssl rand -base64 32`).
예시 값을 넣어두지 않는 이유는, 기동 검증이 "비어 있음"은 막아도 "모두가 아는 값"은 막지 못하기
때문입니다.

> **`docker compose config`를 쓰지 마세요.** 해석된 환경변수를 **평문으로 출력**합니다 —
> 자격 증명과 DB 비밀번호가 그대로 찍혀 터미널 캡처나 CI 로그로 흘러갑니다
> (`docs/06-common-conventions.md` §26). 구조만 확인하려면 `docker compose config --services`.

`.env`에 `FINGUARD_INTERNAL_CREDENTIAL`이 없으면 compose가 거기서 멈춥니다 — 인증이 사실상 꺼진
Internal API를 띄우지 않기 위해서입니다. `up`뿐 아니라 `logs`·`ps`를 포함한 모든 compose 명령이
같은 이유로 멈추므로, 값이 없는데 명령이 안 먹는다면 먼저 이걸 확인하세요.

### 조합

기본 실행은 **시드 없음 + 포트 닫힘**입니다. 필요한 것만 얹습니다.

| 목적 | 명령 |
|---|---|
| 기본 (Gateway 연동 등) | `docker compose up -d` |
| 데모 시드 넣기 | `docker compose -f docker-compose.yml -f docker-compose.demo.yml up -d` |
| 브라우저에서 접근 | `docker compose -f docker-compose.yml -f docker-compose.expose.yml up -d` |
| 데모 + 브라우저 | 위 두 override를 모두 나열 |

데모 시드가 기본이 아닌 이유는 **PostgreSQL 볼륨이 영속**이기 때문입니다. 한 번 들어간 시드는
override를 빼도 사라지지 않습니다. 지우려면 `docker compose down -v`로 볼륨을 날려야 합니다.
되돌릴 수 없는 동작을 기본값으로 두지 않습니다.

## core-api 포트를 호스트에 열어야 할 때

기본은 닫혀 있습니다. Gateway처럼 같은 Compose 네트워크 안에 있는 서비스는 `http://core-api:8080`
으로 닿습니다. 브라우저에서 직접 확인해야 할 때(프론트엔드 작업 등)만 override를 함께 지정합니다.

```bash
docker compose -f docker-compose.yml -f docker-compose.expose.yml up -d
```

기본을 닫아두는 이유는, 같은 포트에 `/api/v1/**`(사내 화면)과 `/internal/**`(서비스 간)이 함께
있기 때문입니다. 포트를 열면 그 PC에 닿는 누구나 Internal API에 요청을 보낼 수 있고, 남는 방어는
공유 자격 증명 문자열 하나뿐입니다. override도 `127.0.0.1`에만 묶으므로 같은 네트워크의 다른
기기에서는 보이지 않습니다.

원래 1차 방어선인 네트워크 격리는 `FinGuard_다음회의전_개발범위_최종본.md` §2.4가 P1으로 미뤘습니다.
그 전까지 이 기본값이 그 자리를 대신합니다.

## 스키마

core-api의 스키마는 Flyway가 소유하고 `ddl-auto`는 `validate`입니다. 컨테이너가 뜰 때 마이그레이션이
자동 실행됩니다. 데모 시드는 `local` 프로파일에서만 로드됩니다 —
`docs/adr/0002-flyway-owns-core-api-schema.md`.

`infrastructure/kubernetes`는 P0 Release Gate가 아닙니다.

---

## Network Zone 배치 (P0 논리적 망 분리)

실제 금융권의 외부망/내부망 분리를 Docker network로 흉내낸 3-존 구조입니다. 물리적 방화벽 수준은 아니지만, 문서 §DB 경계 원칙과 § Gateway 우회 방지 원칙을 컨테이너 네트워크 레벨에서 강제합니다.

| Zone | 목적 | 소속 서비스 | 외부 노출 |
|---|---|---|---|
| `public-zone` | 브라우저/개발자 진입 경로 | frontend, gateway | 예 (127.0.0.1 바인딩) |
| `internal-zone` | 서비스 간 내부 통신 | core-api, agent, mock-finance, ai-risk, opa, gateway | 아니오 |
| `data-zone` | DB 전용 | postgres, core-api | 아니오 (postgres는 dev 편의로 127.0.0.1만) |

### 이 배치가 강제하는 규칙

- Gateway → PostgreSQL 직접 접근 **X** (Gateway는 `data-zone` 미소속)
- Agent → PostgreSQL 직접 접근 **X**
- Frontend → PostgreSQL 직접 접근 **X**
- AI / OPA → PostgreSQL 직접 접근 **X**
- Agent → Mock Finance 직접 접근은 가능하지만 (같은 internal-zone) 정상 경로는 Gateway 경유. P1 K8s NetworkPolicy에서 강하게 차단.

### 다중 존 소속 서비스

- **gateway**: `public-zone` + `internal-zone` (public↔internal 다리)
- **core-api**: `internal-zone` + `data-zone` (internal↔data 다리, DB 접근 유일자)

### 포트 바인딩 정책

`ports:` 를 명시한 서비스도 `127.0.0.1:` 로 바인딩합니다. LAN의 다른 머신에서 접근할 수 없고 오직 개발자의 로컬 머신에서만 접근 가능합니다. 프로덕션에서는 이 포트 매핑도 제거되고 K8s Service/Ingress로 대체됩니다.

### P1 K8s Hardening과의 관계

P0의 Docker network 격리는 "논리적 시뮬레이션"입니다. 컨테이너 간 커널 격리는 제공하지 않으며, 호스트 root 권한이 있으면 우회 가능합니다. 프로덕션 수준의 강제는 P1의 K8s NetworkPolicy + ServiceAccount + `automountServiceAccountToken=false` 조합에서 완성됩니다.