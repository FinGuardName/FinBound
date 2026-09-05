package io.finguard.core.history;

import java.time.Instant;
import java.util.List;

import io.finguard.core.domain.AuditEvent;
import io.finguard.core.domain.PolicyDecision;
import io.finguard.core.domain.Tool;

/** AI Risk Engine에 전달할 완료된 행동 이력. {@code docs/04-api-contract.md} §9. */
public record BehaviorHistoryResponse(
        String agentId, String window, List<CompletedEvent> completedEvents) {

    public BehaviorHistoryResponse {
        completedEvents = List.copyOf(completedEvents);
    }

    public record CompletedEvent(
            String requestId,
            String caseId,
            String targetConsumerId,
            Tool tool,
            Instant requestedAt,
            PolicyDecision decision,
            Boolean success,
            Long latencyMs) {

        static CompletedEvent from(AuditEvent event) {
            return new CompletedEvent(
                    event.getRequestId(),
                    event.getCaseId(),
                    event.getTargetConsumerId(),
                    event.getRequestedTool(),
                    event.getRequestedAt(),
                    event.getDecision(),
                    event.getSuccess(),
                    event.getLatencyMs());
        }
    }
}
