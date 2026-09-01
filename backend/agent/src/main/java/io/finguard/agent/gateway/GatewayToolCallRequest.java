package io.finguard.agent.gateway;

import java.util.List;

import io.finguard.agent.domain.FinancialAction;
import io.finguard.agent.domain.FinancialDataType;
import io.finguard.agent.domain.FinancialTool;

public record GatewayToolCallRequest(
        String agentRunId,
        String passportId,
        FinancialTool tool,
        String targetConsumerId,
        List<FinancialDataType> requestedData,
        FinancialAction action
) {
    public GatewayToolCallRequest {
        requestedData = List.copyOf(requestedData);
    }
}
