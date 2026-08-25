package io.finguard.core.audit;

import java.util.LinkedHashSet;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.finguard.core.domain.AuditCompletion;
import io.finguard.core.domain.AuditEvent;
import io.finguard.core.identifier.RecordIdentifiers;
import io.finguard.core.repository.AuditEventRepository;

/** Business Audit 선저장과 최종 Outcome 갱신. */
@Service
public class AuditService {

    private static final String REQUEST_ID_CONSTRAINT = "uk_audit_event_request_id";

    private final AuditEventRepository auditEvents;

    public AuditService(AuditEventRepository auditEvents) {
        this.auditEvents = auditEvents;
    }

    @Transactional
    public AuditResponse create(AuditCreateRequest request, String trustedVerifiedAgentId) {
        if (auditEvents.existsByRequestId(request.requestId())) {
            throw AuditOperationException.duplicate();
        }

        AuditEvent event =
                new AuditEvent(
                        RecordIdentifiers.auditEventId(),
                        request.requestId(),
                        request.traceId(),
                        trustedVerifiedAgentId,
                        request.agentRunId(),
                        request.requestedAt());
        try {
            return AuditResponse.from(auditEvents.saveAndFlush(event));
        } catch (DataIntegrityViolationException exception) {
            if (violatedRequestIdConstraint(exception)) {
                throw AuditOperationException.duplicate();
            }
            throw AuditOperationException.writeFailed(exception);
        } catch (DataAccessException exception) {
            throw AuditOperationException.writeFailed(exception);
        }
    }

    @Transactional
    public AuditResponse updateOutcome(
            String requestId,
            AuditOutcomeRequest request,
            String trustedVerifiedAgentId) {
        AuditEvent event =
                auditEvents
                        .findByRequestId(requestId)
                        .orElseThrow(AuditOperationException::notFound);
        if (!event.getAgentId().equals(trustedVerifiedAgentId)) {
            throw AuditOperationException.notFound();
        }
        if (event.getStatus() != io.finguard.core.domain.AuditStatus.PROCESSING) {
            throw AuditOperationException.duplicate();
        }

        LinkedHashSet<String> reasonCodes = new LinkedHashSet<>();
        request.reasonCodes().stream().map(Enum::name).sorted().forEach(reasonCodes::add);
        try {
            event.complete(
                    new AuditCompletion(
                            request.decision(),
                            request.systemOutcome(),
                            reasonCodes,
                            request.downstreamReached(),
                            request.responseReleased(),
                            request.behaviorRisk(),
                            request.policyVersion(),
                            request.completedAt()));
            return AuditResponse.from(auditEvents.saveAndFlush(event));
        } catch (IllegalArgumentException exception) {
            throw AuditOperationException.invalidOutcome();
        } catch (DataAccessException exception) {
            throw AuditOperationException.writeFailed(exception);
        }
    }

    private boolean violatedRequestIdConstraint(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof org.hibernate.exception.ConstraintViolationException violation
                    && REQUEST_ID_CONSTRAINT.equals(violation.getConstraintName())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
