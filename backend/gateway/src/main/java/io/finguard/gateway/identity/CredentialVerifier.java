package io.finguard.gateway.identity;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CredentialVerifier {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String LOAN_AGENT_ID = "LOAN-AGENT-01";

    private final List<String> validTokens;

    public CredentialVerifier(@Value("${finguard.credentials.valid-agent-tokens}") List<String> validTokens) {
        this.validTokens = List.copyOf(validTokens);
    }

    public Optional<VerifiedAgentIdentity> verify(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            return Optional.empty();
        }
        String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty() || !validTokens.contains(token)) {
            return Optional.empty();
        }
        return Optional.of(VerifiedAgentIdentity.verified(LOAN_AGENT_ID));
    }
}
