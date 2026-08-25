package io.finguard.core.securityevent;

/** 최소 SecurityAuthEvent를 저장하지 못한 경우. */
public class SecurityEventWriteException extends RuntimeException {

    public SecurityEventWriteException(Throwable cause) {
        super("SECURITY_EVENT_WRITE_FAILED", cause);
    }
}
