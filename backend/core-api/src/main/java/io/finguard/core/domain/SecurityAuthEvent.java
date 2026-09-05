package io.finguard.core.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 인증 실패 등 Gateway 보안 Event. {@code docs/04-api-contract.md} §6 · §14.
 *
 * <p>Business Audit과 <strong>분리</strong>한다. 인증에 실패한 요청은 업무 감사 기록을 만들지 않는다
 * ({@code docs/06-common-conventions.md} §10).
 *
 * <p>Prompt, Document, 전체 Tool Argument, 고객 금융 데이터 같은 민감한 원문을 담지 않는다.
 * {@code sourceFingerprint}는 비식별 해시값이며 선택 항목이다.
 *
 * <p>{@code requestId}에 UNIQUE를 걸지 않는다 — 같은 요청 식별자로 인증이 여러 번 실패하는 것은
 * 정상이고, 오히려 그 반복이 관측해야 할 신호다.
 */
@Entity
@Table(name = "security_auth_events")
public class SecurityAuthEvent {

    /** 예: {@code SEC-001}. */
    @Id
    @Column(name = "security_event_id", nullable = false, length = 64)
    private String securityEventId;

    @Column(name = "request_id", nullable = false, length = 64)
    private String requestId;

    @Column(name = "trace_id", length = 128)
    private String traceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 32)
    private SecurityEventType eventType;

    @Column(name = "reason_code", nullable = false, length = 64)
    private String reasonCode;

    @Column(name = "credential_type", nullable = false, length = 64)
    private String credentialType;

    /** 비식별 해시. 원문 IP나 사용자 식별자를 넣지 않는다. */
    @Column(name = "source_fingerprint", length = 128)
    private String sourceFingerprint;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected SecurityAuthEvent() {
        // JPA
    }

    public SecurityAuthEvent(
            String securityEventId,
            String requestId,
            String traceId,
            SecurityEventType eventType,
            String reasonCode,
            String credentialType,
            String sourceFingerprint,
            Instant occurredAt) {
        this.securityEventId = securityEventId;
        this.requestId = requestId;
        this.traceId = traceId;
        this.eventType = eventType;
        this.reasonCode = reasonCode;
        this.credentialType = credentialType;
        this.sourceFingerprint = sourceFingerprint;
        this.occurredAt = occurredAt;
    }

    public String getSecurityEventId() {
        return securityEventId;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getTraceId() {
        return traceId;
    }

    public SecurityEventType getEventType() {
        return eventType;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public String getCredentialType() {
        return credentialType;
    }

    public String getSourceFingerprint() {
        return sourceFingerprint;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
