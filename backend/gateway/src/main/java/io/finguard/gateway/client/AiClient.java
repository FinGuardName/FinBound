package io.finguard.gateway.client;

import java.time.Instant;

import io.finguard.gateway.dto.BehaviorHistory;
import io.finguard.gateway.dto.BehaviorRiskResult;
import io.finguard.gateway.dto.ResolvedContext;
import io.finguard.gateway.dto.ToolCallRequest;
import io.finguard.gateway.identity.VerifiedAgentIdentity;

public interface AiClient {

    BehaviorRiskResult evaluateBehavior(VerifiedAgentIdentity identity,
                                        ToolCallRequest request,
                                        ResolvedContext context,
                                        BehaviorHistory history,
                                        String requestId,
                                        String traceparent,
                                        Instant requestedAt);
}
