package io.finguard.gateway.client;

import io.finguard.gateway.dto.DownstreamToolResult;
import io.finguard.gateway.dto.ToolCallRequest;

public interface DownstreamClient {

    DownstreamToolResult execute(ToolCallRequest request, String requestId, String traceparent);
}
