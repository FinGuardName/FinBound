package io.finguard.gateway.exception;

public class DownstreamTimeoutException extends RuntimeException {

    public DownstreamTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
