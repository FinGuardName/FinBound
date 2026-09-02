package io.finguard.gateway.client.impl;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import io.finguard.gateway.client.AiClient;
import io.finguard.gateway.dto.BehaviorRiskInput;
import io.finguard.gateway.dto.ToolCallRequest;
import io.finguard.gateway.identity.VerifiedAgentIdentity;

@Component
@Profile("!real-ai")
public class MockAiClient implements AiClient {

    @Override
    public BehaviorRiskInput evaluate(
        VerifiedAgentIdentity identity,
        ToolCallRequest request,
        String requestId
    ) {
        return new BehaviorRiskInput(0.10, "LOW", false);
    }
}
