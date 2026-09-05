package io.finguard.core.securityevent;

import java.time.Instant;

import io.finguard.core.domain.SecurityAuthEvent;
import io.finguard.core.domain.SecurityEventType;

/** 저장된 최소 SecurityAuthEvent. */
public record SecurityEventResponse(
        String securityEventId,
        String requestId,
        String traceId,
        SecurityEventType eventType,
        String reasonCode,
        String credentialType,
        String sourceFingerprint,
        Instant occurredAt) {

    public static SecurityEventResponse from(SecurityAuthEvent event) {
        return new SecurityEventResponse(
                event.getSecurityEventId(),
                event.getRequestId(),
                event.getTraceId(),
                event.getEventType(),
                event.getReasonCode(),
                event.getCredentialType(),
                event.getSourceFingerprint(),
                event.getOccurredAt());
    }
}
