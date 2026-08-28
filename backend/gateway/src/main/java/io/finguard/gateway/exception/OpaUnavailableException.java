package io.finguard.gateway.exception;

public class OpaUnavailableException extends RuntimeException {

    public OpaUnavailableException(String message) {
        super(message);
    }

    public OpaUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
