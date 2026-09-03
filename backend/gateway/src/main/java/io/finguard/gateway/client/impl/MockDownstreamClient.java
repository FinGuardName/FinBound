package io.finguard.gateway.client.impl;

import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import io.finguard.gateway.client.DownstreamClient;
import io.finguard.gateway.dto.DownstreamToolResult;
import io.finguard.gateway.dto.ToolCallRequest;

@Component
@Profile("!real-downstream")
public class MockDownstreamClient implements DownstreamClient {

    @Override
    public DownstreamToolResult execute(ToolCallRequest request, String requestId, String traceparent) {
        return new DownstreamToolResult(
            requestId,
            request.tool(),
            request.targetConsumerId(),
            Map.of("tool", request.tool().name(), "consumerId", request.targetConsumerId()));
    }
}
