package io.finguard.gateway.exception;

public class AuditWriteException extends RuntimeException {

    public AuditWriteException(String message) {
        super(message);
    }

    public AuditWriteException(String message, Throwable cause) {
        super(message, cause);
    }
}
