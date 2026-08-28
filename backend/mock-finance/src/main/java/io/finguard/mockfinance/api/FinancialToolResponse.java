package io.finguard.mockfinance.api;

import java.util.Map;

import io.finguard.mockfinance.domain.FinancialTool;

public record FinancialToolResponse(
        String requestId,
        FinancialTool tool,
        String consumerId,
        Map<String, Object> result
) {
}
