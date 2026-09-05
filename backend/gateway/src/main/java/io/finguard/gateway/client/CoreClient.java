package io.finguard.gateway.client;

import io.finguard.gateway.dto.AuditOutcome;
import io.finguard.gateway.dto.AuditStart;
import io.finguard.gateway.dto.BehaviorHistory;
import io.finguard.gateway.dto.ResolvedContext;
import io.finguard.gateway.dto.ToolCallRequest;
import io.finguard.gateway.identity.VerifiedAgentIdentity;

public interface CoreClient {

    ResolvedContext resolveContext(VerifiedAgentIdentity identity,
                                   ToolCallRequest request,
                                   String requestId,
                                   String traceparent);

    void createAudit(VerifiedAgentIdentity identity, AuditStart auditStart, String traceparent);

    void updateAuditOutcome(VerifiedAgentIdentity identity,
                            String requestId,
                            AuditOutcome outcome,
                            String traceparent);

    BehaviorHistory behaviorHistory(VerifiedAgentIdentity identity,
                                    String window,
                                    String requestId,
                                    String traceparent);

    void recordAuthFailure(String requestId, String traceparent, String reasonCode);
}
