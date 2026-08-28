---
status: accepted
date: 2026-08-25
---

# core-api 스키마는 Flyway가 소유하고 `ddl-auto`는 `validate`로 둔다

`backend/core-api`는 FinGuard PostgreSQL에 접근하는 **유일한 애플리케이션**이다
(`docs/02-architecture.md:234`, `docs/06-common-conventions.md:451`).
그 스키마를 Hibernate가 런타임에 유추하게 두지 않고, `db/migration`의 SQL이 소유한다.
`spring.jpa.hibernate.ddl-auto`는 현재 값인 `validate`를 유지한다.

## 이 결정의 범위 — 팀 합의 사항이 아니다

JPA 의존성과 datasource를 가진 모듈은 `backend/core-api` 하나다. 2026-08-25 기준 실측이다.

```
backend/core-api/build.gradle.kts      spring-boot-starter-data-jpa, postgresql
backend/gateway/build.gradle.kts       없음
backend/agent/build.gradle.kts         없음
backend/mock-finance/build.gradle.kts  없음
```

`docs/05-development-guide.md:481` (Git / File Ownership)이 합의를 요구하는 대상은 **공통 Contract 파일**이다.
`core-api/build.gradle.kts`와 `core-api/src/main/resources/application.yml`은 거기에 해당하지 않는다.
따라서 이 ADR은 Backend 1 모듈 내부 결정이며, 다른 담당자에게는 **통보 사항**이다.

팀원 체감 차이도 없다. 두 방식 모두 `docker compose up` 이후 스키마가 자동으로 준비된다.

## 지금 정해야 하는 이유

`application.yml:11`이 `ddl-auto: validate`인데 마이그레이션 도구가 없고 `db/migration`도 없다.

**주의 — 흔한 오독.** "그래서 지금 core-api가 기동 불가"는 정확하지 않다.
현재 `backend/core-api/src/main/java` 아래에는 `CoreApiApplication.java` 하나뿐이라 **Hibernate가 검증할 엔티티가 없다.**
지금 기동이 안 된다면 그 원인은 도달 가능한 PostgreSQL이 없어서다.

블로커가 되는 시점은 **엔티티를 처음 추가하는 순간**이다. 그 커밋이 Backend 1의 첫 기능 작업이므로,
스키마 소유자를 그 전에 정해야 한다.

## 고려한 대안 — `ddl-auto: update` + `data.sql`

앱을 띄운다는 목적만 보면 성립한다. 기각 근거는 "Hibernate가 스키마를 못 만든다"가 **아니다**(아래 참조).
세 가지가 이 프로젝트에서 구체적으로 깨진다.

**① `data.sql`은 PostgreSQL에서 그냥 동작하지 않는다.**
시드 데이터는 어느 방식을 택하든 필요하다 — 데모가 EMP-101 / CUST-1001 / CUST-9999 /
ACTIVE FinancialCase / ConsumerMandate / PermissionTemplate 행에 의존한다.
그런데 `spring.sql.init.mode`의 기본값은 `embedded`라서 PostgreSQL에서는 `always`로 바꿔야 하고,
`spring.jpa.defer-datasource-initialization`(기본 `false`)을 `true`로 두지 않으면
Hibernate가 테이블을 만들기 **전에** INSERT가 실행되어 실패한다.

즉 `update`를 택해도 SQL 파일은 그대로 쓴다. **실제 차이는 CREATE TABLE을 누가 쓰느냐 하나뿐이다.**

**② Compose 볼륨이 영속이다.**
`infrastructure/docker-compose.yml:18`의 named volume `finguard-postgres` 때문에 매 기동이 깨끗한 DB가 아니다.
`ddl-auto: update`가 안전한 전제 — "깨끗한 DB에 처음부터 생성" — 가 성립하지 않는다.
`update`는 컬럼을 **추가만 하고 제거·변경하지 않으므로**, 컬럼명이나 타입을 바꾸면 옛 컬럼이 남는다.

**③ `audit_events.request_id` UNIQUE는 편의가 아니라 동시성 불변식이다.**
`docs/04-api-contract.md:643` §17이 "동일 Request ID → 실제 Downstream 실행 최대 1회"를 요구한다.
UNIQUE 인덱스에 이걸 걸면 Redis 없이 멱등성이 나온다.
`update`로도 만들 수는 있지만 어노테이션 하나를 빠뜨리면 **조용히** 사라지고 아무것도 실패하지 않는다.
발견한 뒤 추가하려 하면 이미 쌓인 중복 행 때문에 인덱스 생성 자체가 실패한다.

덧붙여, 이 프로젝트의 산출물은 Audit이다. `audit_events`와 `security_auth_events`의
스키마 변경 이력이 남지 않는 것은 주제와 어긋난다.

## 채택한 형태

**baseline DDL은 손으로 타이핑하지 않는다.** `docs/05-development-guide.md:435` §14의 테이블 12개를
매핑까지 맞춰 손으로 쓰는 건 낭비다. 엔티티 매핑을 완성한 뒤 Hibernate 스키마 생성
(jakarta persistence schema-generation)으로 DDL을 **한 번 뽑아** 검토·수정해서 `V1__baseline.sql`로 만든다.
그 이후로는 Flyway가 소유하며, 런타임 `ddl-auto`는 계속 `validate`다.

```
backend/core-api/src/main/resources/
  db/migration/V1__baseline.sql        스키마. Flyway 소유
  db/local/R__demo_seed.sql            시드. local 프로파일에서만 locations에 추가
```

- **시드를 `db/migration` 안에 두지 않는다.** Flyway는 지정한 location의 하위 디렉터리를 재귀 스캔하므로
  안에 두면 모든 환경에 딸려 들어간다.
- 시드는 `INSERT ... ON CONFLICT DO NOTHING`으로 멱등하게 쓴다.
- **TaskPassport는 시드하지 않는다.** authority / consumer / mandate / template만 시드하고,
  Passport는 Effective Permission 계산기가 실제로 발급한다. 시드하면 계산기가 망가져도 데모가 성공한다.
- Flyway 10 이상은 DB별 모듈이 분리돼 있다. `flyway-core`와 함께
  `flyway-database-postgresql`이 필요한지 티켓 1에서 확인하고 필요하면 추가한다.
- 시간 컬럼은 `timestamptz`로 만들고 Java 쪽은 `Instant`로 매핑한다.
  `docs/06-common-conventions.md:42` §3이 모든 Timestamp에 Timezone을 요구한다.
  `LocalDateTime`으로 매핑하면 Hibernate가 생성했다는 이유만으로 이 규약을 만족하지 않는다.

**테스트도 같은 마이그레이션을 쓴다.** Testcontainers PostgreSQL 위에서 `V1__baseline.sql`을 실행한다.
- `create-drop`을 쓰지 않는다. 스키마 정의가 둘로 갈라져, **배포되지 않는 스키마 위에서 테스트가 통과**할 수 있다.
- H2를 쓰지 않는다. array / JSONB / `timestamptz` 동작이 PostgreSQL과 다르다.

## 채택하지 않은 근거 — 다시 꺼내지 말 것

초안에는 **"`ddl-auto: update`는 `sourceVersions` 같은 맵, `allowedTools`/`allowedData` 같은 컬렉션,
UNIQUE 제약, `timestamptz`를 제대로 만들지 못한다"** 는 논거가 있었다.

**이 논거는 틀렸다.** Codex 교차검토에서 반박됐고 수용한다.
깨끗한 DB에 어노테이션이 정확하다면 Hibernate는 컬렉션 테이블도, UNIQUE도, timestamp 컬럼도 전부 만든다.

이 ADR의 근거는 **"Hibernate가 표현하지 못한다"가 아니라 "스키마의 주인이 없으면 조용히 어긋난다"** 이다.
위 ①②③이 그 형태다.

## 이 결정이 적용된 첫 상태는 배포 가능하지 않다

이 ADR을 도입한 커밋(이슈 #17)에는 **마이그레이션 파일이 하나도 없다.** baseline은 엔티티 매핑이
나오는 이슈 #18에서 만든다. 그 사이 상태는 이렇다.

```text
앱이 뜨고 healthcheck 는 healthy
DB 에는 flyway_schema_history 만 있고 업무 테이블이 없다
```

즉 **"마이그레이션이 스키마를 소유한다"는 이 문서의 주장이 아직 참이 아니다.** 소유 구조만 배선돼
있고 소유할 대상이 없다. 이 상태를 배포하거나 릴리스 후보로 삼지 않는다. #18이 병합되기 전까지는
개발 브랜치 위의 중간 상태로만 취급한다.

같은 이유로 `fail-on-missing-locations`를 아직 켜지 못한다 — 가리키는 디렉터리가 없어서 켜면 곧바로
기동에 실패한다. #18에서 디렉터리와 함께 켠다.

## 결과로 명시해야 하는 것들

- `docs/04-api-contract.md:643` §17은 "Downstream 최대 1회"만 말하고 **UNIQUE 제약을 규정하지 않는다.**
  Redis 대신 `audit_events.request_id` UNIQUE로 멱등성을 얻는다는 선택은 문서에 근거가 없으므로
  구현 시 별도로 기록한다.
- `infrastructure/docker-compose.yml`에는 아직 **core 서비스 자체가 없다**(postgres, opa 둘뿐).
  "P0 = Docker Compose"를 만족하려면 Backend 1이 core-api 서비스를 추가해야 한다.
  Flyway는 그 서비스가 뜰 때 자동 실행된다.
- 스키마 변경은 항상 새 마이그레이션 파일로 한다. 적용된 마이그레이션을 수정하지 않는다.

## 관련

- `docs/adr/0001-gateway-mvc-virtual-threads.md` — 같은 성격의 스택 결정
- `FinGuard_기술스택_검토_2026-08-20.md` §2(c) — 최초 제안. 근거 서술은 이 ADR이 대체한다
- `docs/05-development-guide.md:435` §14 — 테이블 12개
- `docs/06-common-conventions.md:451` §24.1 — DB Ownership
