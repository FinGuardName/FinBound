package io.finguard.core.domain;

/** Business AuditEvent 상태. docs/06-common-conventions.md §10. PolicyDecision=BLOCK이 정상 집행되면 COMPLETED다. */
public enum AuditStatus {
    PROCESSING,
    COMPLETED,
    ERROR,
}
