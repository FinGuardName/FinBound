# Infrastructure

P0는 Docker Compose, P1은 Kubernetes hardening입니다. 초기 Compose는 계약/정책 개발을 위한 PostgreSQL과 OPA를 제공합니다. 서비스별 Dockerfile이 추가되면 Core API, Gateway, Agent, Mock Finance, AI Risk, Frontend를 이 파일에 연결합니다.

```bash
docker compose --env-file .env -f infrastructure/docker-compose.yml up -d
docker compose -f infrastructure/docker-compose.yml config
```

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