# Repository guidance

- Treat `docs/04-api-contract.md` and `docs/06-common-conventions.md` as authoritative contracts.
- Preserve `Agent Effective Permission ⊆ Employee Authority`.
- Scope comparison belongs only in the Spring Financial Context Resolver; Rego consumes `ScopeStatus`.
- AI services return risk signals, never authorization decisions.
- Do not log or audit raw prompts, financial payloads, credentials, or secrets.
- P0 is Docker Compose; Kubernetes changes are P1 unless explicitly requested.
- Add tests for ALLOW, BLOCK, fail-closed, and downstream non-reachability as applicable.
