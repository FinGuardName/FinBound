# Repository guidance

- Treat `docs/04-api-contract.md` and `docs/06-common-conventions.md` as authoritative contracts.
- Preserve `Agent Effective Permission ⊆ Employee Authority`.
- Scope comparison belongs only in the Spring Financial Context Resolver; Rego consumes `ScopeStatus`.
- AI services return risk signals, never authorization decisions.
- Gateway must not access FinGuard PostgreSQL directly; use Core internal APIs for context, audit, security events, and behavior history.
- Create business audit records only after successful agent authentication; authentication failures use minimal `SecurityAuthEvent` records.
- Evaluate prompt risk when new or changed untrusted input arrives and reuse `PromptRiskSnapshot` for identical input; behavior risk is evaluated per tool call.
- Do not log or audit raw prompts, financial payloads, credentials, or secrets.
- P0 is Docker Compose; Kubernetes changes are P1 unless explicitly requested.
- Add tests for ALLOW, BLOCK, fail-closed, and downstream non-reachability as applicable.
