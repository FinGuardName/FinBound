package io.finguard.gateway.dto;

import java.math.BigDecimal;

public record PromptRiskSnapshot(
    String evaluationStatus,
    BigDecimal promptRisk,
    boolean detected,
    String inputHash,
    String modelVersion
) {
    public static PromptRiskSnapshot notEvaluated() {
        return new PromptRiskSnapshot("NOT_EVALUATED", BigDecimal.ZERO, false, "sha256:pending", "prompt-guard-1");
    }
}
