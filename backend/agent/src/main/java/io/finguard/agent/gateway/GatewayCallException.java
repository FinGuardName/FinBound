package io.finguard.agent.gateway;

public class GatewayCallException extends RuntimeException {
    private final String errorCode;

    public GatewayCallException(String errorCode) {
        super(errorCode);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
