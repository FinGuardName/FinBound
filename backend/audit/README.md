# Audit

Backend 3이 주도하는 공통 영역입니다. `ToolCallAttempt`와 `ExecutionOutcome`을 분리하고 하나의 AuditEvent를 `PROCESSING → COMPLETED | ERROR`로 완성합니다.

저장: 식별자, ScopeStatus, Risk/Version, PolicyDecision, downstream/response 상태, Reason Code.

저장 금지: 원본 Prompt, 금융 응답 Payload, 실제 개인정보, Credential, Secret.
