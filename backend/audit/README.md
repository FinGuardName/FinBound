# Audit

Backend 3이 Schema와 Runtime Event Contract를 주도하고 Backend 1 Core가 저장을 담당하는 공통 영역입니다. `ToolCallAttempt`와 `ExecutionOutcome`을 분리하고 인증 성공 후 생성한 Business AuditEvent를 `PROCESSING → COMPLETED | ERROR`로 완성합니다. 인증 실패는 Business Audit이 아닌 최소 `SecurityAuthEvent`로 기록합니다.

저장: 식별자, ScopeStatus, Risk/Version, PolicyDecision, downstream/response 상태, Reason Code.

저장 금지: 원본 Prompt, 금융 응답 Payload, 실제 개인정보, Credential, Secret.
