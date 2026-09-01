package io.finguard.gateway.dto;

import java.util.List;

import io.finguard.gateway.contract.FinancialAction;
import io.finguard.gateway.contract.FinancialDataType;
import io.finguard.gateway.contract.FinancialTool;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

// Agent → Gateway
public record ToolCallRequest(
    @NotBlank String agentRunId,
    @NotBlank String passportId,
    @NotNull FinancialTool tool,
    @NotBlank String targetConsumerId,
    @NotEmpty List<FinancialDataType> requestedData,
    @NotNull FinancialAction action
) {
}
