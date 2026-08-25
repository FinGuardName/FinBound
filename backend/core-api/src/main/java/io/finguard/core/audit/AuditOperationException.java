package io.finguard.core.audit;

/** Business Audit API가 구분해 반환해야 하는 조회·중복·저장 실패. */
public class AuditOperationException extends RuntimeException {

    public enum Kind {
        NOT_FOUND,
        DUPLICATE,
        INVALID_OUTCOME,
        WRITE_FAILED,
    }

    private final Kind kind;
    private final String reasonCode;

    private AuditOperationException(Kind kind, String reasonCode, Throwable cause) {
        super(reasonCode, cause);
        this.kind = kind;
        this.reasonCode = reasonCode;
    }

    public static AuditOperationException notFound() {
        return new AuditOperationException(Kind.NOT_FOUND, "CONTEXT_NOT_FOUND", null);
    }

    public static AuditOperationException duplicate() {
        return new AuditOperationException(Kind.DUPLICATE, "DUPLICATE_REQUEST", null);
    }

    public static AuditOperationException invalidOutcome() {
        return new AuditOperationException(Kind.INVALID_OUTCOME, "INVALID_TOOL_REQUEST", null);
    }

    public static AuditOperationException writeFailed(Throwable cause) {
        return new AuditOperationException(Kind.WRITE_FAILED, "AUDIT_WRITE_FAILED", cause);
    }

    public Kind getKind() {
        return kind;
    }

    public String getReasonCode() {
        return reasonCode;
    }
}
