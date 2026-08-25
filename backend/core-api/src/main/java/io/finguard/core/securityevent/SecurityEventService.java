package io.finguard.core.securityevent;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.finguard.core.domain.SecurityAuthEvent;
import io.finguard.core.identifier.RecordIdentifiers;
import io.finguard.core.repository.SecurityAuthEventRepository;

/** 인증 실패 요청을 Business Audit과 분리해 최소 정보로 저장한다. */
@Service
public class SecurityEventService {

    private final SecurityAuthEventRepository securityEvents;

    public SecurityEventService(SecurityAuthEventRepository securityEvents) {
        this.securityEvents = securityEvents;
    }

    @Transactional
    public SecurityEventResponse recordAuthFailure(AuthFailureEventRequest request) {
        SecurityAuthEvent event =
                new SecurityAuthEvent(
                        RecordIdentifiers.securityEventId(),
                        request.requestId(),
                        request.traceId(),
                        request.eventType(),
                        request.reasonCode(),
                        request.credentialType(),
                        request.sourceFingerprint(),
                        request.occurredAt());
        try {
            return SecurityEventResponse.from(securityEvents.saveAndFlush(event));
        } catch (DataAccessException exception) {
            throw new SecurityEventWriteException(exception);
        }
    }
}
