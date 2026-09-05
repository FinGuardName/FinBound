package io.finguard.gateway.interceptor;

import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import io.finguard.gateway.client.CoreClient;
import io.finguard.gateway.filter.RequestIdFilter;
import io.finguard.gateway.identity.CredentialVerifier;
import io.finguard.gateway.identity.VerifiedAgentIdentity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class CredentialInterceptor implements HandlerInterceptor {

    private static final String REASON_AUTH_FAILED = "AGENT_AUTHENTICATION_FAILED";

    private final CredentialVerifier verifier;
    private final CoreClient coreClient;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String requestId = (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_REQUEST_ID);
        if (requestId == null || requestId.isBlank()) {
            requestId = request.getHeader(RequestIdFilter.HEADER_REQUEST_ID);
        }
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        String traceparent = (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_TRACEPARENT);
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);

        Optional<VerifiedAgentIdentity> identity = verifier.verify(authorization);
        if (identity.isEmpty()) {
            return denyUnauthenticated(response, requestId, traceparent);
        }

        request.setAttribute(VerifiedAgentIdentity.ATTRIBUTE_KEY, identity.get());
        return true;
    }

    private boolean denyUnauthenticated(HttpServletResponse response, String requestId, String traceparent) {
        log.warn("Credential verification failed requestId={}", requestId);
        safeRecordAuthFailure(requestId, traceparent);
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        return false;
    }

    private void safeRecordAuthFailure(String requestId, String traceparent) {
        try {
            coreClient.recordAuthFailure(requestId, traceparent, REASON_AUTH_FAILED);
        } catch (Exception e) {
            log.error("SecurityAuthEvent record failed requestId={}", requestId, e);
        }
    }
}
