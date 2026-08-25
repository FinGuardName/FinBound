package io.finguard.agent.gateway;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import io.finguard.agent.domain.PolicyDecision;

public record GatewayToolCallResponse(
        String requestId,
        PolicyDecision decision,
        JsonNode result,
        List<String> reasonCodes
) {
    public GatewayToolCallResponse {
        reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
    }
}
