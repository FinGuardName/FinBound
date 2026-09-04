---
status: accepted
date: 2026-09-05
---

# Frontend는 public-zone과 internal-zone에 동시에 속하고, Nginx allowlist가 그 대가를 치른다

Frontend 컨테이너는 브라우저를 받기 위해 `public-zone`에, Core Public API를 프록시하기 위해
`internal-zone`에 속한다. 그 대가로 Nginx가 `/core-api/api/v1/` 접두사에 맞지 않는 `/core-api`
경로를 404로 끊는다. **이 allowlist는 편의 설정이 아니라 두 존 소속을 성립시키는 조건이다.**

구성은 `infrastructure/docker-compose.frontend-ai.yml`(이슈 #72 / PR #99)이 만든다.

## 흔한 오독 두 가지

### ① "Frontend만 두 존이라 이상하다"가 아니다

여러 존에 걸치는 것 자체는 예외가 아니다. 2026-09-05 기준 실측이다.

```
gateway      public-zone, internal-zone, finance-zone   ← 셋
frontend     public-zone, internal-zone
core-api     internal-zone, data-zone
postgres     data-zone
opa          internal-zone
agent        internal-zone
ai-risk      internal-zone
mock-finance finance-zone
```

경계를 잇는 서비스는 원래 여러 존에 속한다. `gateway`도 호스트에 포트를 연다
(`docker-compose.yml:160` — `127.0.0.1:${GATEWAY_HOST_PORT:-8091}:8081`).
**"브라우저가 닿을 수 있는 유일한 컨테이너"라는 서술은 사실이 아니다.**

실제로 `frontend`가 다른 점은 하나다.

> `frontend`는 **브라우저가 고른 경로를 그대로 받아 내부망으로 전달하는** 유일한 컴포넌트다.
> `gateway`는 자기가 정의한 Tool Call 엔드포인트만 받는다.

임의 경로를 중계한다는 성질이 위험의 근원이고, allowlist가 겨냥하는 것도 그것이다.

### ② `internal-zone`은 Docker가 격리해 주는 망이 아니다

`docker-compose.yml:3-13`에서 `internal: true`가 붙은 것은 `finance-zone`과 `data-zone`뿐이다.
`internal-zone`과 `public-zone`은 평범한 bridge다.

따라서 `/internal/**`을 지키는 것은 "Docker 수준 격리"가 아니라 **① 존 분리 ② 호스트 포트를
열지 않음** 두 가지다. `docker-compose.yml:87-89`가 그 의도를 적어 뒀다.

```
# ports가 아니라 expose다. 호스트에 열지 않는다 — /internal/** 의 방어가 공유 자격 증명 하나뿐이라
# 1차 방어선인 네트워크 격리를 포기하지 않는다.
```

## 왜 internal-zone이 필요한가

`core-api`가 `public-zone`에 없다. `docker-compose.yml:104`가 이유를 적어 뒀다 —
"data-zone은 postgres에, internal-zone은 gateway가 닿기 위해서다. public-zone에는 두지 않는다."

Dashboard 조회와 AgentRun 생성은 브라우저가 Core Public API(`/api/v1/**`)를 직접 부르는 기능이다.
Core를 `public-zone`으로 끌어내지 않으려면 프록시가 양쪽에 발을 걸치는 수밖에 없다.

## 왜 그게 위험한가

`frontend`를 `internal-zone`에 넣는 순간, **브라우저가 고른 경로를 그대로 전달하기만 해도
존 분리가 무의미해진다.** Core의 `/internal/**` 뒤에 남는 방어는 공유 자격 증명 하나뿐이다.

그 값은 `FINGUARD_INTERNAL_CREDENTIAL` 하나이며 core-api·gateway·agent·mock-finance·ai-risk가
나눠 갖는다(`docker-compose.yml`의 각 `secrets:` 블록). 헤더 이름은 상대에 따라 둘로 갈린다 —
Core/ai-risk는 `X-FinGuard-Service-Credential`, Agent/Mock Finance는
`X-FinGuard-Internal-Credential`(`docs/04-api-contract.md` §2·§3.1). **값이 하나라는 것은 P0의
전제이고**(`backend/core-api/src/main/resources/application.yml:24`), 그래서 이 자격증명 하나에
전부를 걸지 않는다.

## 그래서 allowlist가 필수조건이다

`frontend/nginx/default.conf.template:20-37`.

```nginx
location ^~ /core-api/api/v1/ { rewrite ^/core-api(/api/v1/.*)$ $1 break; proxy_pass $core_api_upstream; }
location ^~ /core-api/      { return 404; }
location = /core-api        { return 404; }
```

**deny 목록이 아니라 allow 목록이다.** Core에 새 경로가 생겨도 기본값이 404다.
`^~`는 정규식 location보다 먼저 매칭을 확정시킨다.
`/core-api/api/v1`(뒤 슬래시 없음)은 접두사에 맞지 않아 두 번째 블록에서 404다.

## 측정한 경계 — allowlist는 마지막 방어선이 아니다

**이 절은 추측이 아니라 실행한 결과다.** 저장소의 실제 템플릿으로 Nginx를 띄우고
`core-api`를 붙여 확인했다(2026-09-05).

```
/core-api/api/v1/audit-events                        → 401  (프록시됨. 자격증명 없어 Core가 거부)
/core-api/internal/v1/probe                          → 404  (Nginx가 끊음)
/core-api/actuator/health                            → 404  (Nginx가 끊음)
/core-api/api/v1/..;/..;/actuator/health             → 404  (Core가 거부)
/core-api/api/v1/..;/..;/internal/v1/context/resolve → 401  INTERNAL_CREDENTIAL_INVALID
```

**뒤 두 줄이 중요하다. Nginx는 이 경로들을 막지 못한다.** `..;` 는 Nginx에게 `..` 세그먼트가
아니라 이름이 `..;` 인 평범한 세그먼트라, 정규화 대상이 아니고 `^~ /core-api/api/v1/` 에 매칭된다.
업스트림에는 `/api/v1/..;/..;/actuator/health` 가 그대로 전달된다.

막는 것은 Core다.

- **Spring이 `..;` 를 정규화하지 않는다.** 응답 본문의 `path` 가 `/api/v1/..;/..;/actuator/health`
  그대로였다. 경로 순회가 성립하지 않아 404다.
- **`/internal/` 을 포함하는 경로는 내부 자격증명 필터가 먼저 잡는다.** 401
  `INTERNAL_CREDENTIAL_INVALID` 로 fail-closed 된다.

**따라서 "allowlist가 유일한 방어선"이라고 쓰면 과장이다.** 정확한 서술은 이렇다 — allowlist가
평범한 경로를 끊고, 그것을 우회하는 변형은 Core의 경로 처리와 자격증명 필터가 잡는다. 두 겹이다.

다만 **Nginx 쪽이 새는 것은 사실이므로 Core 쪽 방어를 약화시키면 안 된다.** 특히
내부 자격증명 필터의 경로 매칭을 좁히는 변경은 이 ADR을 다시 읽고 해야 한다.

## 회귀 방어와 그 한계

```
frontend/src/containerContract.test.js:12-15   설정 파일의 문구
.github/workflows/ci.yml:113-114               띄운 뒤 실제 curl
```

CI는 `/core-api/internal/v1/probe`와 `/core-api/actuator/health`에 요청해 **404를 단언한다.**

**한계를 분명히 적어 둔다.** 앞의 테스트는 `toContain` 문자열 검사라 `return 404`가 실제로
있는지, deny 블록이 정말 프록시하지 않는지는 보지 않는다. CI 검사도 위 두 철자만 본다.
**위 절의 `..;` 변형은 두 검사 어느 쪽도 잡지 못한다.** 이 ADR이 그 공백을 기록한다.

## 고려한 대안

**① `core-api`를 `public-zone`에 둔다.** 프록시가 필요 없어지지만 `/internal/**`이 `public-zone`에
노출된다. 지금 구조에서 가장 지키고 싶은 것을 먼저 버리는 선택이다.

**② 브라우저 요청을 `gateway`로 보낸다.** `gateway`는 이미 두 존에 있고 호스트 포트도 연다.
그러나 Gateway의 역할은 **Agent Tool Call 인가 집행**이다. Dashboard 조회를 여기로 보내면
인가 집행점이 BFF를 겸하게 되어 책임 경계가 흐려지고, Tool Call 판단과 무관한 트래픽이 같은
컴포넌트를 지나 감사 관점에서도 나쁘다.

**③ 별도 BFF를 둔다.** 컴포넌트가 하나 늘고 P0 범위를 넘는다. Nginx allowlist로 얻는 것과
실질 차이가 없다.

## 결과

- **Frontend 이미지의 Nginx 설정은 보안 경계다.** 프록시 규칙을 넓히는 변경은 존 구조를 바꾸는
  변경으로 취급한다. `location ^~ /core-api/` 404 블록을 지우면 첫 겹이 사라진다.
- Frontend는 호스트에 `127.0.0.1`로만 열린다. `public-zone`에 있다는 것이 인터넷 노출을 뜻하지 않는다.
- Core에 새 Public 경로를 추가하면 `/api/v1/**` 아래에 두어야 프록시를 통과한다.
  그 밖의 접두사는 Frontend에서 404가 나고, 그것이 의도된 동작이다.
- **Core의 내부 자격증명 필터가 두 번째 겹이다.** 경로 매칭 범위를 좁히면 위 `..;` 변형이
  살아난다. 별도 이슈로 다룰 값어치가 있다.
- P1에서 TLS 종료나 리버스 프록시가 앞에 붙어도 이 allowlist는 유지한다.

## 관련

- `docs/adr/0001-gateway-mvc-virtual-threads.md`, `docs/adr/0002-flyway-owns-core-api-schema.md`
- `infrastructure/docker-compose.yml:87-89`·`:104` — `expose` 근거와 core-api zone 선택
- `infrastructure/docker-compose.frontend-ai.yml` — frontend·ai-risk 오버레이 (PR #99)
- `docs/04-api-contract.md` §2 — Header 계약과 두 자격증명 헤더
- 이슈 #71 — 이 ADR을 쓰기로 한 자리. 당시에는 compose에 frontend 서비스가 없어 대상이 없었다
