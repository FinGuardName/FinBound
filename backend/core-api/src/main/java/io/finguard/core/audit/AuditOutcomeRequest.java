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
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Business Audit의 최종 정책·시스템 실행 결과. */
public record AuditOutcomeRequest(
        PolicyDecision decision,
        @NotNull AuditStatus systemOutcome,
        @NotNull Set<@NotNull ReasonCode> reasonCodes,
        @NotNull Boolean downstreamReached,
        @NotNull Boolean responseReleased,
        Boolean success,
        @Min(0) Integer recordsRead,
        @Min(0) Long latencyMs,
        @Pattern(regexp = "^[A-Z][A-Z0-9_]*$") @Size(max = 64) String errorLocation,
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

    // 아래 셋은 contracts/audit/execution-outcome.schema.json의 조건부 불변식이다.

    @AssertTrue(message = "ERROR requires an errorLocation")
    public boolean isErrorLocated() {
        return systemOutcome != AuditStatus.ERROR || (errorLocation != null && !errorLocation.isBlank());
    }

    @AssertTrue(message = "ERROR must not report success")
    public boolean isErrorUnsuccessful() {
        return systemOutcome != AuditStatus.ERROR || Boolean.FALSE.equals(success);
    }

    @AssertTrue(message = "ALLOW with COMPLETED must report success")
    public boolean isAllowedCompletionSuccessful() {
        return decision != PolicyDecision.ALLOW
                || systemOutcome != AuditStatus.COMPLETED
                || Boolean.TRUE.equals(success);
    }
}
