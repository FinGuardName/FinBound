package io.finguard.gateway.interceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import io.finguard.gateway.client.CoreClient;
import io.finguard.gateway.identity.CredentialVerifier;
import io.finguard.gateway.identity.VerifiedAgentIdentity;

class CredentialInterceptorTest {

    private final CredentialVerifier verifier = new CredentialVerifier(List.of("valid-agent-token"));
    private final CoreClient coreClient = mock(CoreClient.class);
    private final CredentialInterceptor interceptor = new CredentialInterceptor(verifier, coreClient);

    @Test
    void validTokenPopulatesIdentity() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-agent-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean proceed = interceptor.preHandle(request, response, new Object());

        assertThat(proceed).isTrue();
        VerifiedAgentIdentity identity = (VerifiedAgentIdentity)
            request.getAttribute(VerifiedAgentIdentity.ATTRIBUTE_KEY);
        assertThat(identity.agentId()).isEqualTo("LOAN-AGENT-01");
    }

    @Test
    void invalidTokenReturns401AndRecordsSecurityEvent() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer wrong");
        request.setAttribute("finguard.requestId", "REQ-401");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean proceed = interceptor.preHandle(request, response, new Object());

        assertThat(proceed).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        verify(coreClient).recordAuthFailure(eq("REQ-401"), eq(null), eq("AGENT_AUTHENTICATION_FAILED"));
    }
}
