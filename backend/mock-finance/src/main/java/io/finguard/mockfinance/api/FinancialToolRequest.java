package io.finguard.mockfinance.api;

import io.finguard.mockfinance.domain.FinancialTool;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record FinancialToolRequest(
        @NotBlank String requestId,
        @NotNull FinancialTool tool,
        @NotBlank String targetConsumerId
) {
}
