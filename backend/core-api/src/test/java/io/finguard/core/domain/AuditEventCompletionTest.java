package io.finguard.core.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * 완료 처리가 실행 측정값을 실제로 남기는지 확인한다.
 *
 * <p>{@code contracts/audit/execution-outcome.schema.json}이 {@code success}·{@code recordsRead}·
 * {@code latencyMs}를 정의하고 {@code docs/04-api-contract.md} §9가 Behavior History 응답에
 * {@code success}·{@code latencyMs}를 싣는다. 완료 경로가 이 값을 받지 않으면 AI Risk에 넘어가는
 * 이력이 전부 null이 되어 §9가 약속한 응답이 성립하지 않는다.
 */
class AuditEventCompletionTest {

    private static final Instant REQUESTED_AT = Instant.parse("2026-08-25T12:00:00Z");
    private static final Instant COMPLETED_AT = Instant.parse("2026-08-25T12:00:01Z");

    @Test
    void storesExecutionMeasurementsSoBehaviorHistoryIsNotNull() {
        AuditEvent event = processingEvent();

        event.complete(
                new AuditCompletion(
                        PolicyDecision.ALLOW,
                        AuditStatus.COMPLETED,
                        Set.of(),
                        true,
                        true,
                        true,
                        1,
                        120L,
                        null,
                        new BigDecimal("0.08"),
                        "loan-review-policy-1",
                        COMPLETED_AT));

        assertThat(event.getSuccess()).isTrue();
        assertThat(event.getRecordsRead()).isEqualTo(1);
        assertThat(event.getLatencyMs()).isEqualTo(120L);
    }

    /**
     * {@code contracts/audit/execution-outcome.schema.json}:48-104의 조건부 불변식. DTO에서도 막지만
     * 도메인에도 둔다 — 기존 패턴이고, 이 record를 직접 만드는 경로가 검증을 우회하면 안 된다.
     */
    @Test
    void blockedOutcomeCannotClaimTheResponseWasReleased() {
        assertThatThrownBy(() -> completion(PolicyDecision.BLOCK, AuditStatus.COMPLETED,
                        Set.of("CASE_SCOPE_VIOLATION"), false, true, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void blockedOutcomeRequiresAtLeastOneReasonCode() {
        assertThatThrownBy(() -> completion(PolicyDecision.BLOCK, AuditStatus.COMPLETED,
                        Set.of(), false, false, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void errorOutcomeCannotClaimTheResponseWasReleased() {
        assertThatThrownBy(() -> completion(PolicyDecision.ALLOW, AuditStatus.ERROR,
                        Set.of("DOWNSTREAM_TIMEOUT"), true, true, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void errorOutcomeRequiresAtLeastOneReasonCode() {
        assertThatThrownBy(() -> completion(PolicyDecision.ALLOW, AuditStatus.ERROR,
                        Set.of(), true, false, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void allowedCompletionMustHaveReachedDownstreamAndReleasedTheResponse() {
        assertThatThrownBy(() -> completion(PolicyDecision.ALLOW, AuditStatus.COMPLETED,
                        Set.of(), false, true, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private AuditCompletion completion(
            PolicyDecision decision,
            AuditStatus systemOutcome,
            Set<String> reasonCodes,
            boolean downstreamReached,
            boolean responseReleased,
            Boolean success) {
        return new AuditCompletion(
                decision,
                systemOutcome,
                reasonCodes,
                downstreamReached,
                responseReleased,
                success,
                null,
                null,
                systemOutcome == AuditStatus.ERROR ? "DOWNSTREAM" : null,
                null,
                "loan-review-policy-1",
                COMPLETED_AT);
    }

    private AuditEvent processingEvent() {
        return new AuditEvent(
                "AUD-1",
                "REQ-1",
                "trace-1",
                "LOAN-AGENT-01",
                "RUN-1",
                "LOAN-2026-001",
                "CUST-1001",
                Tool.CREDIT_SCORE_READ,
                REQUESTED_AT);
    }
}
