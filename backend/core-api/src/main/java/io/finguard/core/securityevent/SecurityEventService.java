package io.finguard.core.securityevent;

import java.time.Instant;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.finguard.core.domain.ReasonCode;
import io.finguard.core.domain.SecurityAuthEvent;
import io.finguard.core.domain.SecurityEventType;
import io.finguard.core.identifier.RecordIdentifiers;
import io.finguard.core.repository.SecurityAuthEventRepository;

/** 인증 실패 요청을 Business Audit과 분리해 최소 정보로 저장한다. */
@Service
public class SecurityEventService {

    /** 브라우저(Vue) → Core {@code /api/v1/**} 경로의 자격 증명 종류. {@code docs/04} §2. */
    private static final String CORE_API_CREDENTIAL_TYPE = "CORE_API_BEARER";

    private final SecurityAuthEventRepository securityEvents;

    public SecurityEventService(SecurityAuthEventRepository securityEvents) {
        this.securityEvents = securityEvents;
    }

    /**
     * Core 자신의 {@code /api/v1/**} 인증·인가 거부를 기록한다.
     * {@code docs/04-api-contract.md} §2.
     *
     * <p>{@link AuthFailureEventRequest}를 재사용하지 않는 이유는 그 DTO가 {@code credentialType}을
     * {@code AGENT_SERVICE}로, {@code reasonCode}를 {@code AGENT_AUTHENTICATION_FAILED}로
     * {@code @AssertTrue}에 못박아 뒀기 때문이다. Gateway가 Agent 인증 실패를 보고하는 용도로 <b>의도적으로
     * 좁힌</b> 계약이라, Core API 사유를 담자고 그 제약을 푸는 것은 방향이 거꾸로다.
     *
     * <p>{@code sourceFingerprint}는 비워 둔다. 넣을 수 있는 값은 클라이언트 IP뿐인데, IPv4 공간은
     * 전수 계산이 가능해서 소금 없는 해시는 되돌릴 수 있다. 그러면 {@link
     * io.finguard.core.domain.SecurityAuthEvent} Javadoc이 말하는 "비식별"이 성립하지 않는다. 제대로
     * 하려면 고정 비밀키 HMAC이 필요하고 그건 키 관리를 동반하므로 별도 결정으로 남긴다. 스키마도
     * 계약도 이 항목을 선택으로 둔다.
     */
    @Transactional
    public void recordCoreApiAuthFailure(
            String requestId, String traceId, ReasonCode reasonCode, Instant occurredAt) {
        securityEvents.saveAndFlush(
                new SecurityAuthEvent(
                        RecordIdentifiers.securityEventId(),
                        requestId,
                        traceId,
                        SecurityEventType.AUTH_FAILURE,
                        reasonCode.name(),
                        CORE_API_CREDENTIAL_TYPE,
                        null,
                        occurredAt));
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
