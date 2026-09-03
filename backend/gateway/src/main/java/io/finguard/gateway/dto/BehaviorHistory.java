package io.finguard.gateway.dto;

import java.time.Instant;
import java.util.List;

import io.finguard.gateway.contract.FinancialDataType;
import io.finguard.gateway.contract.FinancialTool;
import io.finguard.gateway.contract.PolicyDecision;

public record BehaviorHistory(
    String agentId,
    String window,
    List<CompletedEvent> completedEvents
) {
    public BehaviorHistory {
        completedEvents = completedEvents == null ? List.of() : List.copyOf(completedEvents);
    }

    public record CompletedEvent(
        String requestId,
        String caseId,
        String targetConsumerId,
        FinancialTool tool,
        Instant requestedAt,
        PolicyDecision decision,
        Boolean success,
        Long latencyMs,
        List<FinancialDataType> requestedData
    ) {
        public CompletedEvent {
            requestedData = requestedData == null ? List.of() : List.copyOf(requestedData);
        }
    }
}
