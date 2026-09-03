package io.finguard.core.dashboard;

/** 없는 AuditEvent를 조회했다. 인가 실패가 아니라 대상 부재다. */
public class AuditEventNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AuditEventNotFoundException(String auditEventId) {
        super("No audit event " + auditEventId);
    }
}
