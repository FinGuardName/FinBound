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
    List<String> reasonCodes,
    String error
) {
    public static ToolCallResponse allow(String requestId, Map<String, Object> result) {
        return new ToolCallResponse(requestId, PolicyDecision.ALLOW, result, null, null);
    }

    public static ToolCallResponse block(String requestId, List<String> reasonCodes) {
        return new ToolCallResponse(requestId, PolicyDecision.BLOCK, null, reasonCodes, null);
    }

    /**
     * Gateway가 정상 인가(ALLOW) 이후 downstream/시스템 장애로 응답을 전달하지 못한 경우.
     * decision을 비워 정책 BLOCK과 구분한다.
     */
    public static ToolCallResponse systemError(String requestId, String errorCode, List<String> reasonCodes) {
        return new ToolCallResponse(requestId, null, null, reasonCodes, errorCode);
    }
}
