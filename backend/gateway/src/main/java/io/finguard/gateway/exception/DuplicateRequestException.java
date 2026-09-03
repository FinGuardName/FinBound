package io.finguard.gateway.exception;

public class DuplicateRequestException extends RuntimeException {

    public DuplicateRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
