package io.finguard.gateway.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class CredentialVerifierTest {

    private final CredentialVerifier verifier =
        new CredentialVerifier(List.of("valid-agent-token"));

    @Test
    void validBearerTokenIsAccepted() {
        Optional<VerifiedAgentIdentity> identity = verifier.verify("Bearer valid-agent-token");
        assertThat(identity).isPresent();
        assertThat(identity.get().agentId()).isEqualTo("LOAN-AGENT-01");
        assertThat(identity.get().credentialStatus()).isEqualTo("VERIFIED");
    }

    @Test
    void unknownTokenIsRejected() {
        assertThat(verifier.verify("Bearer other")).isEmpty();
    }

    @Test
    void missingHeaderIsRejected() {
        assertThat(verifier.verify(null)).isEmpty();
    }

    @Test
    void nonBearerSchemeIsRejected() {
        assertThat(verifier.verify("Basic dXNlcjpwYXNz")).isEmpty();
    }

    @Test
    void emptyBearerTokenIsRejected() {
        assertThat(verifier.verify("Bearer ")).isEmpty();
    }
}
