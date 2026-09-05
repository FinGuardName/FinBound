package io.finguard.gateway.client.impl;

import java.time.Instant;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import io.finguard.gateway.client.AiClient;
import io.finguard.gateway.dto.BehaviorHistory;
import io.finguard.gateway.dto.BehaviorRiskResult;
import io.finguard.gateway.dto.ResolvedContext;
import io.finguard.gateway.dto.ToolCallRequest;
import io.finguard.gateway.identity.VerifiedAgentIdentity;

@Component
@Profile("!real-ai")
public class MockAiClient implements AiClient {

    @Override
    public BehaviorRiskResult evaluateBehavior(VerifiedAgentIdentity identity,
                                               ToolCallRequest request,
                                               ResolvedContext context,
                                               BehaviorHistory history,
                                               String requestId,
                                               String traceparent,
                                               Instant requestedAt) {
        return new BehaviorRiskResult(0.10, "LOW", false, -0.01, "COLD_START",
            "behavior-features-1", "iforest-1");
    }
}
