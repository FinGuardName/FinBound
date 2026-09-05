package io.finguard.mockfinance.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.finguard.mockfinance.api.FinancialApiError;
import io.finguard.mockfinance.config.MockFinanceProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class InternalCredentialFilter extends OncePerRequestFilter {
    public static final String INTERNAL_CREDENTIAL_HEADER = "X-FinGuard-Internal-Credential";

    private final byte[] expectedCredential;
    private final ObjectMapper objectMapper;

    public InternalCredentialFilter(
            MockFinanceProperties properties,
            ObjectMapper objectMapper
    ) {
        expectedCredential = properties.internalCredential().getBytes(StandardCharsets.UTF_8);
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String presentedCredential = request.getHeader(INTERNAL_CREDENTIAL_HEADER);
        if (!credentialMatches(presentedCredential)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), new FinancialApiError(
                    "INTERNAL_CREDENTIAL_INVALID",
                    "A valid internal service credential is required"
            ));
            return;
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/internal/");
    }

    private boolean credentialMatches(String presentedCredential) {
        if (presentedCredential == null) {
            return false;
        }
        byte[] presentedBytes = presentedCredential.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedCredential, presentedBytes);
    }
}
