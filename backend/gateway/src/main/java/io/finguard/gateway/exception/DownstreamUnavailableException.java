package io.finguard.gateway.exception;

public class DownstreamUnavailableException extends RuntimeException {

    public DownstreamUnavailableException(String message) {
        super(message);
    }

    public DownstreamUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
