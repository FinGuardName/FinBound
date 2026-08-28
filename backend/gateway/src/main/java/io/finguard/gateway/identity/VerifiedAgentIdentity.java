package io.finguard.gateway.identity;

public record VerifiedAgentIdentity(String agentId, String credentialStatus) {

    public static final String ATTRIBUTE_KEY = "finguard.verifiedAgentIdentity";

    public static VerifiedAgentIdentity verified(String agentId) {
        return new VerifiedAgentIdentity(agentId, "VERIFIED");
    }
}
