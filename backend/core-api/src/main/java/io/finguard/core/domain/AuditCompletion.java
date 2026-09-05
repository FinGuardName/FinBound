package io.finguard.core.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

/** 한 번만 적용할 수 있는 Business Audit 최종 결과. */
public record AuditCompletion(
        PolicyDecision decision,
        AuditStatus systemOutcome,
        Set<String> reasonCodes,
        boolean downstreamReached,
        boolean responseReleased,
        Boolean success,
        Integer recordsRead,
        Long latencyMs,
        String errorLocation,
        BigDecimal behaviorRisk,
        Severity severity,
        Boolean riskFlagged,
        String policyVersion,
        Instant completedAt) {

    public AuditCompletion {
        if (systemOutcome == null || systemOutcome == AuditStatus.PROCESSING) {
            throw new IllegalArgumentException("Audit completion requires a final system outcome");
        }
        if (systemOutcome == AuditStatus.COMPLETED && decision == null) {
            throw new IllegalArgumentException("Completed audit requires a policy decision");
        }
        // contracts/audit/execution-outcome.schema.json:48-104의 조건부 불변식.
        // 이걸 걸지 않으면 스키마가 금지한 상태가 감사 기록으로 남는다 — 거짓 증거가 된다.
        if (decision == PolicyDecision.BLOCK) {
            if (downstreamReached) {
                throw new IllegalArgumentException("Blocked audit cannot reach downstream");
            }
            if (responseReleased) {
                throw new IllegalArgumentException("Blocked audit cannot release a response");
            }
            if (reasonCodes == null || reasonCodes.isEmpty()) {
                throw new IllegalArgumentException(
                        "Blocked audit requires at least one reason code");
            }
            // downstream에 닿지 않았으므로 실행 측정값이 존재할 수 없다. 두 스키마가 함께 금지한다.
            // false나 0도 값이다 — "측정하지 않았음"과 "측정했더니 0"은 다른 사실이다.
            if (success != null || recordsRead != null || latencyMs != null) {
                throw new IllegalArgumentException(
                        "Blocked audit cannot carry execution measurements");
            }
        }
        if (systemOutcome == AuditStatus.ERROR) {
            if (errorLocation == null || errorLocation.isBlank()) {
                throw new IllegalArgumentException("Error outcome requires an errorLocation");
            }
            if (!Boolean.FALSE.equals(success)) {
                throw new IllegalArgumentException("Error outcome cannot report success");
            }
            if (responseReleased) {
                throw new IllegalArgumentException("Error outcome cannot release a response");
            }
            if (reasonCodes == null || reasonCodes.isEmpty()) {
                throw new IllegalArgumentException(
                        "Error outcome requires at least one reason code");
            }
        }
        if (decision == PolicyDecision.ALLOW && systemOutcome == AuditStatus.COMPLETED) {
            if (!Boolean.TRUE.equals(success)) {
                throw new IllegalArgumentException("Allowed completion must report success");
            }
            if (!downstreamReached || !responseReleased) {
                throw new IllegalArgumentException(
                        "Allowed completion must reach downstream and release the response");
            }
        }
        if (behaviorRisk != null
                && (behaviorRisk.compareTo(BigDecimal.ZERO) < 0
                        || behaviorRisk.compareTo(BigDecimal.ONE) > 0)) {
            throw new IllegalArgumentException("Behavior risk must be between zero and one");
        }
        if ((severity == null) != (riskFlagged == null)) {
            throw new IllegalArgumentException(
                    "Severity and risk flag must be recorded together");
        }
        if (decision != null && severity == null) {
            throw new IllegalArgumentException(
                    "Policy decisions require severity and risk flag");
        }
        if (decision == null && severity != null) {
            throw new IllegalArgumentException(
                    "System failures without a policy decision cannot carry policy risk fields");
        }
        if (completedAt == null) {
            throw new IllegalArgumentException("Audit completion requires completedAt");
        }
        reasonCodes = Set.copyOf(reasonCodes);
    }
}
