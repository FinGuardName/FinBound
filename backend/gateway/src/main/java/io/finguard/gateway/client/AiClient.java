package io.finguard.gateway.client;

import io.finguard.gateway.dto.BehaviorRiskInput;
import io.finguard.gateway.dto.ToolCallRequest;
import io.finguard.gateway.identity.VerifiedAgentIdentity;

public interface AiClient {

    BehaviorRiskInput evaluate(
        VerifiedAgentIdentity identity,
        ToolCallRequest request,
        String requestId
    );
}
