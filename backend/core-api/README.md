# Core API

Backend 1 소유 영역입니다.

## 책임

- Employee / Employee Authority
- Consumer / Consumer Mandate Seed
- Permission Template / Financial Case
- Agent Effective Permission / Task Passport
- Financial Context Resolver / Scope Status
- Dashboard Read-only API

Scope 비교의 Single Source of Truth는 이 모듈입니다. 최종 `ALLOW/BLOCK`은 결정하지 않습니다.

권장 패키지: `employee`, `consumer`, `mandate`, `permission`, `financialcase`, `passport`, `context`, `dashboard`.
