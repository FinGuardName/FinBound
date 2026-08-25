package io.finguard.core.audit;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

import io.finguard.core.domain.AuditStatus;
import io.finguard.core.domain.PolicyDecision;
import io.finguard.core.domain.ReasonCode;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Business Audit의 최종 정책·시스템 실행 결과. */
public record AuditOutcomeRequest(
        PolicyDecision decision,
        @NotNull AuditStatus systemOutcome,
        @NotNull Set<@NotNull ReasonCode> reasonCodes,
        @NotNull Boolean downstreamReached,
        @NotNull Boolean responseReleased,
        @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal behaviorRisk,
        @Size(max = 64) String policyVersion,
        @NotNull Instant completedAt) {

    public AuditOutcomeRequest {
        reasonCodes = reasonCodes == null ? null : Set.copyOf(reasonCodes);
    }

    @AssertTrue(message = "systemOutcome must be COMPLETED or ERROR")
    public boolean isFinalOutcome() {
        return systemOutcome == null || systemOutcome != AuditStatus.PROCESSING;
    }

    @AssertTrue(message = "COMPLETED requires a policy decision")
    public boolean isDecisionComplete() {
        return systemOutcome != AuditStatus.COMPLETED || decision != null;
    }

    @AssertTrue(message = "BLOCK must not reach downstream")
    public boolean isBlockStoppedBeforeDownstream() {
        return decision != PolicyDecision.BLOCK || Boolean.FALSE.equals(downstreamReached);
    }
}
