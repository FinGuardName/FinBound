---
status: accepted
date: 2026-08-24
---

# Gateway는 Spring Cloud Gateway 대신 Spring MVC + 가상 스레드를 쓴다

`backend/gateway`는 이름과 달리 리버스 프록시가 아니라 **정책 집행 지점(PEP)이자 오케스트레이터**다.
Spring Cloud Gateway(이하 SCG)가 제공하는 기능을 하나도 쓰지 않으면서 Reactor의 비용만 내고 있으므로,
`spring-boot-starter-web` + Java 21 가상 스레드로 바꾼다.

## 왜 SCG가 이 모듈에 맞지 않는가

**SCG의 기능을 실제로 쓰고 있지 않다.** 2026-08-24 기준 실측이다.

```
backend/gateway/src                     GatewayApplication.java 11줄이 전부
application.yml                         라우트 0개, predicate 0개, filter 0개
build.gradle.kts:17                     spring-cloud-starter-gateway-server-webflux
build.gradle.kts:19                     reactor-test (미사용)
```

**앞으로도 라우팅 성격의 일이 거의 없다.** `docs/02-architecture.md` §7.2가 이 모듈에 부여한 책임 12개
(Tool Call Interception, Credential 검증, AuthorizationContext 생성, OPA Client, ALLOW/BLOCK Enforcement,
Fail-closed 등)에 **"라우팅"이 없다.**

시퀀스 다이어그램(`docs/02-architecture.md:143`)의 성공 경로는 서비스 간 호출 **7회**다 —
Audit 선저장, Context resolve, Behavior History, Behavior Risk, OPA, Mock Finance(ALLOW일 때만),
ExecutionOutcome. 이 중 프록시 성격은 Mock Finance 호출 하나뿐이고, 그마저 OPA 판단 뒤에 조건부로 일어난다.
나머지는 결과를 받아 다음 단계 입력으로 가공하는 **순차 오케스트레이션**이다.

**가상 스레드가 WebFlux를 정당화하던 유일한 근거를 없앤다.** 7회 순차 I/O 동안 스레드를 점유하지 않는 것이
WebFlux의 실질 이점인데, `spring.threads.virtual.enabled=true` 한 줄로 대체된다.
Java 21은 이미 toolchain에 고정돼 있다(`build.gradle.kts:30`).

**지금이 가장 싸다.** 이 모듈에는 Reactor 코드가 한 줄도 없다. 의존성 교체 자체는 1시간 미만이다.
오케스트레이션을 `Mono` 체인으로 한 번 작성한 뒤에는 각 단계의 에러 처리 의미를 전부 다시 해석해야 하므로
비용이 계단식으로 오른다.

## "Reactor 비용"이 가리키는 것

측정된 성능 비용이 아니다. **쓰지 않는 프로그래밍 모델을 계속 떠안는 개발·운영 비용**을 말한다.

`Mono`나 `Flux`를 한 줄도 쓰지 않아도 `spring-cloud-starter-gateway-server-webflux`
(`build.gradle.kts:17`)를 쓰는 한 Reactor와 Netty가 런타임에 들어온다. 그리고 이 모듈에 로직이
붙기 시작하는 순간 다음이 따라온다.

- **Reactor·Netty 런타임** — 이벤트 루프, 버퍼 관리가 함께 온다.
- **작성 규칙** — `Mono` 체인, 리액티브 에러 처리, 컨텍스트 전파, **블로킹 호출 금지**.
  Core·AI·OPA·Finance 클라이언트를 전부 논블로킹으로 써야 하고, 실수로 블로킹하면
  이벤트 루프를 막는다.
- **테스트 방식** — `reactor-test`, `StepVerifier` 등 별도 도구와 숙련도가 필요하다.
  이 모듈은 팀에서 한 명(Backend2)이 담당한다.
- **되돌리기 비용** — 구현이 쌓인 뒤 MVC로 바꾸려면 각 단계의
  **empty / error / cancel 의미를 전부 다시 해석해야 한다.**

반대급부인 Reactor의 강점 — 대규모 비동기 스트리밍, 백프레셔 — 은 이 모듈의 요구사항에 없다.
따라서 **이점 없이 제약만 받는다**는 것이 이 ADR의 판단이다.

가상 스레드가 Reactor를 전면 대체한다는 뜻은 아니다. 스트리밍과 백프레셔는 대체하지 못한다.
대체하는 것은 **"순차 블로킹 I/O 동안 스레드를 점유하지 않는다"** 는 한 가지이고,
이 모듈이 Reactor에서 실제로 필요로 하는 것이 그 한 가지뿐이다.

## 라우팅과 오케스트레이션은 다르다

"순차 실행이니까 라우팅 아니냐"는 반론이 나올 수 있다. **순차 실행이라서 라우트가 아닌 것이 아니다.**
SCG의 필터 체인도 순차 실행이다. 갈리는 지점은 다른 데 있다.

```
라우팅          들어온 요청을 어느 목적지로 보낼지 고르고, 그 요청을 전달한다
오케스트레이션   Gateway가 여러 백엔드를 직접 호출하고 결과를 조합해 새 응답을 만든다
```

FinGuard는 후자다. `POST /gateway/v1/tool-calls`(`docs/04-api-contract.md:152`) 하나가 들어오면
Gateway는 그 요청을 어디로도 전달하지 않는다. 대신 **자기가 outbound 호출을 만들어 낸다.**

```
POST /internal/v1/audits                                    Core
POST /internal/v1/context/resolve                           Core
GET  /internal/v1/agents/{agentId}/behavior-history          Core
POST /internal/v1/risk/behavior                             AI Risk
POST /v1/data/finguard/authorization/decision               OPA
     (ALLOW일 때만) Mock Finance 호출                         Finance
PATCH /internal/v1/audits/{requestId}/outcome               Core
```

이 호출들은 SCG Route가 아니다. 원 요청과 **경로도 본문도 다른, 처리 도중 생성되는 별개 요청**이다.
SCG의 Predicate가 고를 수 있는 대상이 아니다.

SCG global filter 안에 이 오케스트레이션을 전부 구현하는 것도 기술적으로 가능하다.
그러나 그렇게 하면 **필터가 사실상 비즈니스 서비스가 되어 `Mono` 체인만 복잡해지고,
정작 SCG의 라우팅 이점은 그대로 쓰지 못한다.** 그게 이 결정의 요지다.

### Predicate로 인가를 판단하지 않는다

혼동하기 쉬운 지점이라 명시한다. 아래는 **Predicate의 일이 아니다.**

```
고객 범위(customerScope) 비교
도구 권한(toolScope) 비교
직원 권한(employeeAuthority) 비교
ALLOW / BLOCK 결정
```

Scope Status 계산은 **Financial Context Resolver**(F11, Backend 1)가,
정책 조합 판단은 **OPA**가 한다(`docs/02-architecture.md:9`).
Predicate는 "요청을 어디로 보낼지" 고르는 조건이지 금융 인가를 결정하는 정책 엔진이 아니다.
여기에 인가 로직을 넣으면 `docs/02`가 세운 Core / OPA / Gateway 경계가 무너진다.

## 고려한 대안

**SCG 유지.** SCG가 카테고리상 틀린 것은 아니다 — 요청 크기 제한, rate limit, 헤더 위생, 추적, 조건부 forward는
정당한 API 게이트웨이 관심사이고 SCG global filter로 구현할 수 있다. 기각 근거는 "쓸 수 없다"가 아니라
**"쓰지 않으면서 Reactor 비용만 낸다"** 이다. 모듈 이름이 `gateway`라는 이유만으로 SCG를 유지할 값어치는 없다.

**`spring-cloud-starter-gateway-server-webmvc`.** Servlet 스택 위에서 SCG의 라우트·필터를 쓰는 변형이다.
Reactor 없이 SCG의 정렬된 필터 계층을 원한다면 이쪽이 맞다. **P0에서는 그 필터 계층이 필요할 만큼
바깥 제어(size/rate limit/헤더 제거)가 복잡하지 않아** 선택하지 않는다. 필요해지면 재검토 대상이다.

**라우팅이 실제로 필요해지는 경우.** SCG를 영구히 버리는 것이 아니라 **역할을 섞지 않는 것**이다.
아래 중 하나라도 성립하면 SCG가 값어치를 갖는다.

```
경로 기반 중계 대상이 여러 서비스로 늘어날 때
인증 전 rate limit · 요청 크기 제한 · Request ID를 모든 경로에 공통 적용할 때
카나리 배포나 트래픽 가중치 분산이 필요할 때
```

그때는 **PEP 앞에 별도 프로세스(Edge Gateway)로 둔다.**

```
외부 요청
  → Edge Gateway (SCG)        인증 전 rate limit, 요청 크기 제한, Request ID, Route 선택
  → FinGuard PEP (MVC)        인증, Context / Risk / OPA, ALLOW / BLOCK 집행
```

라우터와 정책 집행을 한 프로세스에 합칠 이유가 없다. `docs/02-architecture.md`의
"Gateway 우회 방지"(P1) 관점에서도 분리가 낫다.

## 결과로 명시해야 하는 것들

WebFlux를 버리면 Reactor가 구조적으로 강제하던 것들이 **직접 작성해야 할 항목**으로 바뀐다.
아래를 인수 조건으로 잡지 않으면 이 결정은 순손실이다.

- **다운스트림별 동시성 상한.** 가상 스레드는 생성이 싸서, 과부하 시 Core·AI·OPA·Finance를 향해
  더 많은 요청이 동시에 블로킹될 수 있다. 백프레셔가 공짜로 오지 않는다. 커넥션 풀 상한과 함께 명시한다.
- **본문 필수 응답의 명시적 거부.** `RestClient.body()`는 본문이 없으면 **null을 반환한다(던지지 않는다).**
  Context·History·AI·OPA·Finance 클라이언트는 "본문 필수 + 스키마 검증" 경계를 공유해야 한다.
  Audit 쓰기처럼 본문이 없는 게 정상인 호출은 완료 신호로 다룬다.
- **단계별 타임아웃과 타입 있는 예외.** `docs/04-api-contract.md` §16이 상황별 Reason Code를 요구한다.
  일반 예외 → 500 핸들러 하나로는 계약을 못 지킨다. **모든 실패가 BLOCK도 아니다** — Audit 선저장 실패는
  Downstream 미호출이고, Finance 타임아웃은 ERROR다.
- **Audit 확정 경로.** OPA 판단 이후 단계에서 예외가 나도 `PROCESSING` 레코드가 남지 않도록
  결정적 마무리 경로가 필요하다.
- **`spring.main.keep-alive=true`.** 가상 스레드는 데몬 스레드라 함께 설정한다.
- **가상 스레드 pinning 측정.** Java 21은 `synchronized` 블록 안의 블로킹에서 캐리어 스레드를 고정한다
  (JDK 24/JEP 491에서 해소). 특정 라이브러리를 미리 지목하지 말고 `-Djdk.tracePinnedThreads=full`로 측정한다.
- **병렬성 손실 검토.** Context resolve와 History→Behavior Risk는 서로 독립이라 겹칠 수 있다.
  순차 MVC 코드는 이 겹침을 잃어 인가 지연이 늘 수 있다. 측정 후 필요하면 가상 스레드 executor로 복원한다.
  (Java 21의 structured concurrency는 preview다.)
- **문서 정리.** `docs/02`·`docs/05`·E2E 시나리오가 여전히 "Spring Cloud Gateway"를 이름으로 명시한다.
  테스트와 문서는 프레임워크 이름이 아니라 **동작**(ALLOW/BLOCK, fail-closed, 금융 API 비도달)을 단언해야 한다.

교체 작업 자체(스타터 교체, `build.gradle.kts:6-12` Spring Cloud BOM 제거, `:19` `reactor-test` 제거,
프로퍼티 2줄)는 1시간 미만이지만, **위 항목을 포함한 실제 작업량은 1~2 person-day다.**

## 채택하지 않은 근거 — 다시 꺼내지 말 것

초안에는 **"Reactor 체인에서 `Mono.empty()`가 전파되면 OPA 판단을 건너뛰어 fail-open이 된다"** 는 논거가 있었다.
**이 논거는 틀렸다.** Codex 교차검토에서 반박됐고 검토 결과를 수용한다.

빈 신호가 전파되면 OPA뿐 아니라 **Mock Finance 호출도 건너뛴다.** 인가 없이 금융 행위가 일어나지 않으므로
fail-open이 아니다. 정확한 이름은 **fail-stop이면서 계약 위반**이다 — ALLOW/BLOCK 본문 없는 200 응답,
`PROCESSING`에 갇힌 Audit, Reason Code 부재. 심각한 결함이지만 인가 우회는 아니다.

MVC 대비 논거("`RestClient`는 NPE를 던져 우연히 fail-closed가 된다")도 틀렸다. `RestClient.body()`는
null을 반환하며, 우연한 NPE로 인한 500은 계약이 요구하는 Reason Code를 만들지 못하므로
**계약 준수 fail-closed가 아니다.** 두 스택 모두 "본문 필수" 경계를 명시적으로 작성해야 한다.

이 결정의 근거는 안전성이 아니라 **쓰지 않는 프레임워크의 비용을 내지 않는 것**이다.

## 관련

- `docs/02-architecture.md` §7.2, 시퀀스 다이어그램(`:143`) — 이 모듈의 책임 정의
- `docs/04-api-contract.md` §16 — Reason Code와 실패별 처리
- `FinGuard_기술스택_검토_2026-08-20.md` §2(a) — 최초 제안. 위 "채택하지 않은 근거"에 해당하는 서술이 남아 있다
