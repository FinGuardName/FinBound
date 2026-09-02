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

    /**
     * BLOCK은 downstream에 닿지 않았으므로 실행 측정값이 존재할 수 없다.
     *
     * <p>{@code contracts/audit/execution-outcome.schema.json}과 {@code audit-event.schema.json}이
     * 둘 다 BLOCK에서 이 셋을 금지한다. 여기서 막지 않으면 스키마가 거부하는 상태가 그대로 저장된다 —
     * {@code errorLocation}이 스키마 필수인데 컬럼이 없어 모든 ERROR 기록이 위반이었던 것과 같은 계열이다.
     *
     * <p>{@code false}나 {@code 0}도 값이다. "측정하지 않았음"과 "측정했더니 0"은 다른 사실이라
     * 값의 내용이 아니라 존재 여부로 판정한다.
     */
    @AssertTrue(message = "BLOCK must not carry execution measurements")
    public boolean isBlockWithoutExecutionMeasurements() {
        return decision != PolicyDecision.BLOCK
                || (success == null && recordsRead == null && latencyMs == null);
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

    // 아래 다섯도 같은 스키마의 조건부 불변식인데 그동안 빠져 있었다. 없으면 "차단했다면서
    // 응답은 내보냈다"나 "이유 없이 차단했다" 같은 거짓 사실이 감사 기록으로 남는다.

    @AssertTrue(message = "BLOCK must not release a response")
    public boolean isBlockWithoutResponseRelease() {
        return decision != PolicyDecision.BLOCK || Boolean.FALSE.equals(responseReleased);
    }

    /** {@code @NotNull} Set은 빈 집합을 통과시킨다. 스키마는 최소 하나를 요구한다. */
    @AssertTrue(message = "BLOCK requires at least one reason code")
    public boolean isBlockExplained() {
        return decision != PolicyDecision.BLOCK || (reasonCodes != null && !reasonCodes.isEmpty());
    }

    @AssertTrue(message = "ERROR must not release a response")
    public boolean isErrorWithoutResponseRelease() {
        return systemOutcome != AuditStatus.ERROR || Boolean.FALSE.equals(responseReleased);
    }

    @AssertTrue(message = "ERROR requires at least one reason code")
    public boolean isErrorExplained() {
        return systemOutcome != AuditStatus.ERROR
                || (reasonCodes != null && !reasonCodes.isEmpty());
    }

    @AssertTrue(message = "ALLOW with COMPLETED must reach downstream and release the response")
    public boolean isAllowedCompletionDelivered() {
        return decision != PolicyDecision.ALLOW
                || systemOutcome != AuditStatus.COMPLETED
                || (Boolean.TRUE.equals(downstreamReached) && Boolean.TRUE.equals(responseReleased));
    }
}
