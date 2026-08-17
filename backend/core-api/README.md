# Core API

Backend 1 소유 영역입니다.

## 책임

- Employee / Employee Authority
- Consumer / Consumer Mandate Seed
- Permission Template / Financial Case
- Agent Effective Permission / Task Passport
- Financial Context Resolver / Scope Status
- Runtime Context Internal API
- Business Audit / SecurityAuthEvent Persistence API
- Behavior History Read API
- Dashboard Read-only API

FinGuard PostgreSQL의 애플리케이션 접근 주체는 이 모듈입니다. Gateway는 Context, Audit, Security Event, Behavior History를 Core Internal API로만 요청합니다. Scope 비교의 Single Source of Truth도 이 모듈이며 최종 `ALLOW/BLOCK`은 결정하지 않습니다.

권장 패키지: `employee`, `consumer`, `mandate`, `permission`, `financialcase`, `passport`, `context`, `audit`, `securityevent`, `behaviorhistory`, `dashboard`.
