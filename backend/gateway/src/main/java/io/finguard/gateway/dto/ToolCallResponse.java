package io.finguard.gateway.dto;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.finguard.gateway.contract.PolicyDecision;

// Gateway → Agent
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ToolCallResponse(
    String requestId,
    PolicyDecision decision,
    Map<String, Object> result,
    List<String> reasonCodes
) {
    public static ToolCallResponse allow(String requestId, Map<String, Object> result) {
        return new ToolCallResponse(requestId, PolicyDecision.ALLOW, result, null);
    }

    public static ToolCallResponse block(String requestId, List<String> reasonCodes) {
        return new ToolCallResponse(requestId, PolicyDecision.BLOCK, null, reasonCodes);
    }
}
