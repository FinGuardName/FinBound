package io.finguard.core.audit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import io.finguard.core.domain.AuditEvent;
import io.finguard.core.domain.AuditStatus;
import io.finguard.core.domain.SecurityAuthEvent;
import io.finguard.core.domain.SecurityEventType;
import io.finguard.core.repository.AuditEventRepository;
import io.finguard.core.repository.SecurityAuthEventRepository;
import io.finguard.core.securityevent.AuthFailureEventRequest;
import io.finguard.core.securityevent.SecurityEventService;
import io.finguard.core.securityevent.SecurityEventWriteException;

class PersistenceFailureTest {

    private static final Instant NOW = Instant.parse("2026-08-25T12:00:00Z");

    @Test
    void mapsBusinessAuditStorageFailureToFailClosedReason() {
        AuditEventRepository repository = mock(AuditEventRepository.class);
        when(repository.saveAndFlush(any(AuditEvent.class)))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));
        AuditService service = new AuditService(repository);
        AuditCreateRequest request =
                new AuditCreateRequest(
                        "REQ-FAIL",
                        "trace-fail",
                        "RUN-FAIL",
                        "UNTRUSTED-BODY-AGENT",
                        null,
                        null,
                        null,
                        AuditStatus.PROCESSING,
                        NOW);

        assertThatThrownBy(() -> service.create(request, "LOAN-AGENT-01"))
                .isInstanceOf(AuditOperationException.class)
                .extracting("reasonCode")
                .isEqualTo("AUDIT_WRITE_FAILED");
    }

    @Test
    void rejectsDuplicateBeforeAttemptingAnotherInsert() {
        AuditEventRepository repository = mock(AuditEventRepository.class);
        when(repository.existsByRequestId("REQ-DUPLICATE")).thenReturn(true);
        AuditService service = new AuditService(repository);
        AuditCreateRequest request =
                new AuditCreateRequest(
                        "REQ-DUPLICATE",
                        null,
                        "RUN-FAIL",
                        "LOAN-AGENT-01",
                        null,
                        null,
                        null,
                        AuditStatus.PROCESSING,
                        NOW);

        assertThatThrownBy(() -> service.create(request, "LOAN-AGENT-01"))
                .isInstanceOf(AuditOperationException.class)
                .extracting("reasonCode")
                .isEqualTo("DUPLICATE_REQUEST");
        verify(repository, never()).saveAndFlush(any(AuditEvent.class));
    }

    @Test
    void mapsSecurityEventStorageFailureToItsOwnReason() {
        SecurityAuthEventRepository repository = mock(SecurityAuthEventRepository.class);
        when(repository.saveAndFlush(any(SecurityAuthEvent.class)))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));
        SecurityEventService service = new SecurityEventService(repository);
        AuthFailureEventRequest request =
                new AuthFailureEventRequest(
                        "REQ-AUTH-FAIL",
                        null,
                        SecurityEventType.AUTH_FAILURE,
                        "AGENT_AUTHENTICATION_FAILED",
                        "AGENT_SERVICE",
                        null,
                        NOW);

        assertThatThrownBy(() -> service.recordAuthFailure(request))
                .isInstanceOf(SecurityEventWriteException.class)
                .hasMessage("SECURITY_EVENT_WRITE_FAILED");
    }
}
