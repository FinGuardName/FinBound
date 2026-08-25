package io.finguard.core.history;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.finguard.core.domain.AuditStatus;
import io.finguard.core.repository.AuditEventRepository;

class BehaviorHistoryServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-25T12:00:00Z");

    private final AuditEventRepository auditEvents = mock(AuditEventRepository.class);
    private final BehaviorHistoryService service =
            new BehaviorHistoryService(auditEvents, Clock.fixed(NOW, ZoneOffset.UTC));

    @ParameterizedTest
    @CsvSource({
        "1s, 2026-08-25T11:59:59Z",
        "5m, 2026-08-25T11:55:00Z",
        "2h, 2026-08-25T10:00:00Z",
        "1d, 2026-08-24T12:00:00Z"
    })
    void queriesOnlyCompletedEventsInsideTheRequestedWindow(String window, Instant cutoff) {
        when(auditEvents
                        .findByAgentIdAndStatusAndRequestedAtGreaterThanEqualOrderByRequestedAtDesc(
                                "LOAN-AGENT-01", AuditStatus.COMPLETED, cutoff))
                .thenReturn(List.of());

        service.findCompletedEvents("LOAN-AGENT-01", window);

        verify(auditEvents)
                .findByAgentIdAndStatusAndRequestedAtGreaterThanEqualOrderByRequestedAtDesc(
                        "LOAN-AGENT-01", AuditStatus.COMPLETED, cutoff);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "0m",
                "-5m",
                "5minutes",
                "m5",
                "999999999999999999999m",
                "9223372036854775807d",
                "9223372036854775807s"
            })
    void rejectsInvalidWindowsWithoutQueryingAuditHistory(String window) {
        assertThatThrownBy(() -> service.findCompletedEvents("LOAN-AGENT-01", window))
                .isInstanceOf(InvalidBehaviorHistoryWindowException.class);

        verifyNoInteractions(auditEvents);
    }
}
