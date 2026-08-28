package io.finguard.core.domain;

import static org.assertj.core.api.Assertions.assertThat;

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
