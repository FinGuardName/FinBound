---
status: accepted
date: 2026-09-05
---

# 배포는 GitHub Actions가 빌드하고 SSM이 EC2 한 대에 밀어 넣는다. Frontend를 Vercel로 떼지 않는다

여덟 서비스를 **EC2 한 대**에 `docker compose`로 모아 올린다. 이미지는 **GitHub Actions**가 빌드해
컨테이너 레지스트리에 밀고, EC2는 받아서 실행한다. Actions가 EC2에 닿는 경로는 SSH가 아니라 **AWS SSM
Send-Command**다. **Frontend를 Vercel로 분리하는 안은 검토했고 채택하지 않았다.**

**레지스트리 선택은 이 문서에서 갈라져 나갔다** — 처음에는 GHCR로 정했으나 조직 정책에 막혀
Amazon ECR로 바꿨다. 그 결정은 [ADR 0005](0005-images-live-in-ecr-not-ghcr.md)에 있다.

네 개의 독립된 판단이 한 문서에 있다. **따로 뒤집을 수 있다** — Vercel을 다시 고른다고 SSM까지
버릴 이유는 없다.

이 문서를 쓰는 시점에 저장소에 배포 파이프라인은 **없다.** 실측이다.

```
.github/workflows/ 6개  →  registry push · kubectl · helm · deploy 스텝 0건
```

---

## ① CI/CD는 GitHub Actions — 새 도구를 얹지 않는다

이미 워크플로 6개가 돌고 시크릿 저장소도 쓰고 있다(`SONAR_TOKEN`). 새 계정·새 서버·새 인증이
필요 없다. 마감을 앞두고 새 CI/CD를 세우면 그 자체가 새 장애 지점이 된다.

**다만 "이미 빌드가 돌고 있으니 push만 붙이면 된다"는 말은 사실이 아니다.** 초안에 그렇게 썼다가
정정한다. 실제 상태는 이렇다.

| 이미지 | 어디서 빌드되나 | 배포에 쓸 수 있나 |
|---|---|---|
| `agent`, `mock-finance` | `service-containers.yml` → `service-container-smoke.sh:174-175` | 스모크용 태그 |
| `ai-risk` | `ai-risk-container.yml` | 스모크용 태그 |
| `frontend` | **`ci.yml:85`** (`finbound-frontend:ci`) | 스모크용 태그 |
| **`core-api`, `gateway`** | **없다** | **생산 워크플로 자체가 없다** |

그리고 **compose 여섯 서비스가 전부 `build:`이고 `image:` 참조가 하나도 없다**
(`docker-compose.yml:61,126,165,196`, `docker-compose.frontend-ai.yml:8`).
`infrastructure/README.md:47`은 대상 호스트에서 빌드하는 것을 전제로 쓰여 있다.

**따라서 레지스트리 기반 배포는 "push 한 줄"이 아니라 다음을 새로 만드는 일이다:**
core-api·gateway 이미지 생산 워크플로, 다이제스트 태그 체계(`latest` 금지),
`image:`를 참조하는 배포용 compose 오버레이.

그럼에도 Actions를 고르는 이유는 유효하다. **다른 CI/CD를 세우면 이 일을 하고 나서 기존 검증
빌드까지 다시 짜야 한다.**

## ② Actions → EC2는 SSM Send-Command

**GitHub 러너의 IP는 고정이 아니다.** SSH로 가면 22번을 `0.0.0.0/0`에 열거나, 바뀌는 IP 목록을
보안그룹에 계속 반영해야 한다. 앞은 **존 분리를 주장하는 제출물에 전 세계로 열린 22번이 딸려
가는 그림**이고, 뒤는 사람이 잊는 순간 배포가 멈춘다.

SSM은 인바운드가 0개다. 80/443만 남기고 22번을 열지 않는다.

**"SSH냐 SSM이냐"는 사실 이지선다가 아니다.** self-hosted 러너(아웃바운드만), 오버레이 VPN,
pull 방식 에이전트도 있다. 그중 SSM을 고른 건 **AWS를 이미 쓰기로 했고 추가 런타임이 없기**
때문이지, 다른 방법이 안전하지 않아서가 아니다.

비용은 정직하게: 인스턴스 프로필(`AmazonSSMManagedInstanceCore`), SSM Agent(공식 AMI에 대개
포함 — **확인이 필요하다. "항상 있다"는 보장은 없다**), IAM OIDC 공급자와 역할. 마지막이 제일
오래 걸린다. **SSH가 더 빠르게 출발하는 건 사실이다.**

## ③ Frontend를 Vercel에 올리지 않는다

**이 판단이 가장 논쟁적이고, 초안의 근거 하나는 틀렸다.** 정정해서 남긴다.

### 틀렸던 근거 — "CORS 때문에 안 된다"

초안은 Vercel에 올리면 출처가 갈려 CORS가 필요해지고, 백엔드에 CORS 설정이 없으니
preflight에서 막힌다고 썼다. **CORS 설정이 없는 것은 사실이다** — 확인했고,
`CoreApiCredentialFilterHttpTest.java:239`가 preflight가 401로 떨어지는 것을 의도적으로 검증한다.

**그런데 이건 결정적 근거가 아니다.** Vercel은 외부 오리진으로 가는 rewrite를 지원한다.
`vercel.json`에 `/core-api/:path*` → EC2로 rewrite를 걸면 **브라우저에는 계속 동일 출처**이고
CORS 문제가 사라진다. 초안은 "브라우저가 EC2를 직접 부른다"는 약한 상대만 반박했다.

### 남는 근거 — allowlist를 옮길 수 없다

Nginx가 하는 일은 경로 프록시가 아니다. `frontend/nginx/default.conf.template`:

```nginx
location ^~ /core-api/api/v1/ {          # Public API 접두사만 통과
    if ($uri ~ "\.\.") { return 404; }   # 정규화를 피한 경로 차단 (PR #102)
    rewrite ^/core-api(/api/v1/.*)$ $1 break;
    proxy_pass $core_api_upstream;
}
location ^~ /core-api/ { return 404; }   # 나머지 /core-api 경로 전부 차단
```

핵심은 `if ($uri ~ "\.\.")` 다. **정규화가 끝난 `$uri`를 보고 `..;` 같은 변형을 잡는다.**
[ADR 0003](0003-frontend-spans-public-and-internal-zones.md)이 이 allowlist를 Frontend가 두 존에
속하는 것을 성립시키는 **조건**으로 못 박았다.

`vercel.json`의 rewrite는 경로 패턴 매칭이다. **정규화 이후 값을 검사하는 이런 규칙을 표현할
수단이 없다.** 그래서 Vercel로 가면 EC2 앞에 같은 Nginx를 다시 세워야 하고, 그러면 Vercel은
정적 파일 CDN 하나만 남는다. **그 하나를 위해 배포 대상이 둘로 늘어난다.**

### 그리고 검사가 배포된 것을 검증하지 않게 된다

allowlist는 두 겹으로 지켜지고 있다.

- `frontend/src/containerContract.test.js:12-15` — conf에 규칙 **문자열이 있는지** 검사한다
  (런타임 동작 검사가 아니다)
- **`ci.yml:112-122`** — 실제 컨테이너를 띄워 `/core-api/internal/v1/probe`와 `/actuator/health`가
  404인지 확인하고, `..;`·`..%3B` 변형에 대해서는 **upstream이 그 요청을 보지 못했는지**까지 본다

Vercel로 옮기면 이 검사들은 저장소에 남아 계속 통과하는데 **배포된 시스템에는 그 방어가 없다.**

### Vercel이 실제로 이기는 지점

공정하게: **도메인 없이 HTTPS가 바로 나온다**(`*.vercel.app`). EC2는 호스트명을 마련해야 한다.
아래 "선행 조건"으로 넘긴다.

트래픽은 심사 한 번이다. Vercel의 CDN은 이 프로젝트에 없는 문제를 푼다.

---

## 그래서 어떤 모양인가

```
호스트명 → Caddy(자동 HTTPS) → frontend 컨테이너(Nginx allowlist) → core-api
```

`core-api`는 **기본 compose에서 호스트 포트를 아예 열지 않는다**(`docker-compose.yml:87`).
`127.0.0.1`로 여는 것은 선택적 오버레이(`docker-compose.expose.yml:12`)다. 초안이 "127.0.0.1만
듣는다"고 쓴 것은 부정확했다 — 실제로는 그보다 더 닫혀 있다.

**"CI E2E 형상 = 배포 형상"이라고 초안에 썼는데 이것도 과장이다.** `frontend-ai-e2e.ps1:79`는
소스에서 `up --build`를 한다. 레지스트리도 SSM도 Caddy도 TLS도 없다. 정확히는 **애플리케이션 토폴로지는
같고 전달 경로는 다르다.**

E2E 8개 중 `ai-risk.spec.js` 2건은 AI Risk를 직접 호출해 Frontend를 거치지 않는다. 나머지는
`baseURL`이 Frontend다(`playwright.config.js:11`).

## 선행 조건 — 아직 정하지 않은 것

**HTTPS를 위한 호스트명이 필요하다.**

| 방법 | 비용 | 비고 |
|---|---|---|
| 도메인 구입 | 연 1~2만원 | 제일 깔끔 |
| `sslip.io` / `nip.io` | 무료 | `<IP>.sslip.io`가 그 IP로 풀린다. **Elastic IP가 필요하다** |
| Cloudflare Tunnel | 무료 | 인바운드를 아예 안 연다 |

초안은 "Let's Encrypt는 맨 IP에 발급하지 않는다"고 단정했다. **이 단정은 근거를 확인하지 못했다**
— IP 인증서 발급이 가능해졌다는 지적이 있었고 검증하지 못했다. 호스트명을 쓰는 편이 단순하다는
판단은 유지하되, "CA가 거부한다"를 근거로 삼지 않는다.

## 구현 전에 반드시 풀어야 할 것

설계가 아니라 지뢰다. 착수 시 하나씩 이슈로 뗀다.

1. **X-Forwarded-For를 Rate Limit이 그대로 믿는다** (`RateLimitFilter.java:67`). 이번 배포가
   만든 결함이 아니라 원래 있던 것인데, **인터넷에서 닿게 되면서 실제로 발동하는 자리가 된다.**

   > 함께 지적받은 "X-Forwarded-Proto 이중 프록시" 는 **이 스택에서는 성립하지 않는다.**
   > Nginx가 `$scheme`으로 덮어쓰는 것은 사실이지만(`default.conf.template:39`),
   > `core-api`에 `forward-headers-strategy` 설정이 없고 `X-Forwarded-*`를 읽는 코드도 없다.
   > 읽지 않는 헤더가 틀리는 것은 결함이 아니다. 나중에 Core가 이 헤더를 쓰기 시작하면
   > 그때 되살아난다.
2. ~~**GHCR 인증**~~ — **해소됐다.** 이 지뢰가 실제로 터졌고, 그 결과 레지스트리를 GHCR에서
   **Amazon ECR로 바꿨다.** EC2가 자기 인스턴스 역할로 인증하므로 호스트에 저장되는 자격증명이
   없다. 경위와 버린 대안은 [ADR 0005](0005-images-live-in-ecr-not-ghcr.md).
3. **런타임 비밀값 전달** — `docker-compose.yml:16-27`의 secrets가 호스트 환경값 5개를
   그대로 읽는다(`POSTGRES_PASSWORD`·`FINGUARD_INTERNAL_CREDENTIAL` 외 3개). SendCommand
   파라미터나 로그로 흐르면 안 된다. `infrastructure/README.md:40` — `POSTGRES_PASSWORD`를 바꿔도
   기존 볼륨에는 반영되지 않는다.
4. **Flyway가 기동 시 자동 실행된다**(`application.yml:12`). 배포가 실패해도 스키마는 이미
   올라간 뒤일 수 있다. **이미지만 되돌리는 롤백으로는 부족하다.** 백업·복구 리허설이 없다.
5. ~~**Caddy가 저장소에 없다**~~ — **해소됐다.** PR #107이 `infrastructure/caddy/Caddyfile` 과
   `docker-compose.public.yml`(인증서 영속 볼륨 포함)을 넣었다.
6. **배포 동시 실행 잠금**이 없다.

## 대가 — 무엇을 포기했나

- **CDN이 없다.** 심사 한 번짜리 트래픽이라 감수한다.
- **EC2 한 대가 단일 장애점이다.** 이중화하지 않는다. **다만 허용 가능한 다운타임·데이터 손실
  범위를 아직 적지 않았다** — 단일 호스트가 맞는지는 그걸 정해야 판정할 수 있다.
- **인스턴스 크기 — 재고 정했다. t3.medium(4GiB)이면 된다.**

  처음에 선언된 메모리 한도의 합(약 3.75GiB)을 근거로 t3.large를 잡았는데 **그건 "쓸 수 있는
  최대"지 "쓰는 양"이 아니었다.** 2026-09-05에 여덟 서비스를 띄우고 실제 데모 경로로
  AgentRun 60건을 만든 뒤 측정했다.

  ```
  ai-risk       904 MiB / 1GiB (88%)     ← 지배적. ML 모델이 상주한다
  core-api      299 MiB / 1GiB
  agent         213 MiB / 512MiB
  gateway       185 MiB / 512MiB
  mock-finance  157 MiB / 512MiB
  postgres       56 MiB / 무제한
  opa            39 MiB / 무제한
  frontend       13 MiB / 256MiB
  ─────────────────────────────
  합계         1.82 GiB
  ```

  Caddy(~30MiB)와 OS·Docker 데몬(~400MiB)을 더해 **약 2.3GiB**. t3.medium에서 1.7GiB가 남는다.

  **한계:** 60건을 순차로 보냈다. 동시 요청이면 JVM 힙이 더 오른다. 브라우저 E2E는 돌리지 않았다.

- **진짜 위험은 인스턴스 크기가 아니라 `ai-risk` 의 컨테이너 한도다.** 자기 한도의 88%에 붙어
  있어서 인스턴스에 자리가 남아도 OOM 으로 죽는다. 배포에서는 `AI_RISK_MEM_LIMIT=1280m` 으로
  올린다.
- **SSM 설정이 SSH보다 느리게 출발한다.**

`ai-risk`의 ML 모델은 **빌드 단계에서 내려받아 이미지에 굽는다**(`ai-risk/Dockerfile:19-25`).
런타임 메모리가 아니라 **이미지 크기와 최초 pull 시간** 문제다. 초안이 이를 런타임 비용으로
적은 것은 틀렸다.
