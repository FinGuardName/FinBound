# FinGuard Final MVP Documentation

이 문서 세트는 마지막 수정본을 기반으로, 팀에서 다시 합의한 MVP 방향을 반영해 정리한 최종 기준 문서다.

## 문서 구성

| 문서 | 역할 |
|---|---|
| `00-overview.md` | 문제정의, 핵심 가치, MVP/P1 범위, 기술 구성 |
| `01-feature-spec.md` | P0 기능 요구사항과 P1 확장 기능 |
| `02-architecture.md` | P0 논리/배포 아키텍처, 책임 경계, P1 Kubernetes 고도화 |
| `03-ai-spec.md` | Prompt Injection, Isolation Forest, 데이터·평가·Threshold 정책 |
| `04-api-contract.md` | Spring·Gateway·FastAPI·OPA·Dashboard 간 Contract |
| `05-development-guide.md` | 4인 고정 역할분담, 개발 순서, DoD, 최종 데모 |
| `06-common-conventions.md` | Enum, Reason Code, 상태값, Risk/Scope/Decision 규칙 |
| `07-test-scenarios.md` | 핵심 E2E·AI 독립가치·장애·우회 테스트 시나리오 |
| `FINAL-CHANGELOG.md` | 마지막 수정본 대비 최종 반영사항 |

## 최종 Core Invariant

```text
Agent Effective Permission
⊆
Employee Authority
```

MVP의 권한 계산은 다음을 기준으로 한다.

```text
Employee Authority
∩ Permission Template
∩ Financial Case
∩ Consumer Mandate
        ↓
Agent Effective Permission
        ↓
Task Passport
```

## 역할 분담 — 고정

```text
Backend 1 — Financial Context / Permission
Backend 2 — FinGuard Core / Policy
Backend 3 — Agent / Mock Finance / Audit
Frontend & AI
```

## P0 / P1 원칙

- P0는 Case-aware Runtime Authorization, Prompt/Behavior AI, Audit/Dashboard, Docker Compose까지 구현한다.
- Kubernetes NetworkPolicy/RBAC는 P1 보안 고도화다.
- Consumer Mandate는 P0에서 Seed Data로 적용하고 CRUD UI는 P1이다.
- PII/Response Inspection, MASK/APPROVAL, Human Approval은 P1이다.
