package io.finguard.gateway.dto;

import java.util.Map;

import io.finguard.gateway.contract.FinancialTool;

public record DownstreamToolResult(
    String requestId,
    FinancialTool tool,
    String consumerId,
    Map<String, Object> result
) {
}
