package io.finguard.core.securityevent;

import java.time.Instant;

import io.finguard.core.domain.SecurityEventType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** {@code POST /internal/v1/security-events/auth-failure}의 최소 입력. */
public record AuthFailureEventRequest(
        @NotBlank @Size(max = 64) String requestId,
        @Size(max = 128) String traceId,
        @NotNull SecurityEventType eventType,
        @NotBlank @Size(max = 64) String reasonCode,
        @NotBlank @Size(max = 64) String credentialType,
        @Pattern(regexp = "^sha256:[0-9a-fA-F]{64}$") String sourceFingerprint,
        @NotNull Instant occurredAt) {

    @AssertTrue(message = "eventType must be AUTH_FAILURE")
    public boolean isAuthFailure() {
        return eventType == null || eventType == SecurityEventType.AUTH_FAILURE;
    }

    @AssertTrue(message = "reasonCode must describe agent authentication failure")
    public boolean isAuthenticationFailureReason() {
        return reasonCode == null || reasonCode.equals("AGENT_AUTHENTICATION_FAILED");
    }

    @AssertTrue(message = "credentialType must be AGENT_SERVICE")
    public boolean isAgentServiceCredential() {
        return credentialType == null || credentialType.equals("AGENT_SERVICE");
    }
}
