package io.finguard.gateway.client.impl;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import io.finguard.gateway.client.AiClient;
import io.finguard.gateway.dto.RiskInput;
import io.finguard.gateway.dto.ToolCallRequest;
import io.finguard.gateway.identity.VerifiedAgentIdentity;

@Component
@Profile("!real-ai")
public class MockAiClient implements AiClient {

    @Override
    public RiskInput evaluate(VerifiedAgentIdentity identity, ToolCallRequest request, String requestId) {
        return new RiskInput(0.05, false, 0.10, "LOW", false);
    }
}
