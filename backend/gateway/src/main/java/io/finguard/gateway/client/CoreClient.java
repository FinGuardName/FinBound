package io.finguard.gateway.client;

import io.finguard.gateway.dto.ResolvedContext;
import io.finguard.gateway.dto.ToolCallRequest;
import io.finguard.gateway.identity.VerifiedAgentIdentity;

public interface CoreClient {

    ResolvedContext resolveContext(
        VerifiedAgentIdentity identity,
        ToolCallRequest request,
        String requestId
    );

    void recordAuthFailure(String requestId, String reasonCode);
}
