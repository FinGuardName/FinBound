package io.finguard.gateway.exception;

public class CoreUnavailableException extends RuntimeException {

    public CoreUnavailableException(String message) {
        super(message);
    }

    public CoreUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
