package io.finguard.gateway.client;

import io.finguard.gateway.dto.RiskInput;
import io.finguard.gateway.dto.ToolCallRequest;
import io.finguard.gateway.identity.VerifiedAgentIdentity;

public interface AiClient {

    RiskInput evaluate(VerifiedAgentIdentity identity, ToolCallRequest request, String requestId);
}
