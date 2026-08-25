package io.finguard.core.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Agent에 유입된 비신뢰 입력의 등록 기록. {@code docs/04-api-contract.md} §3.
 *
 * <p><strong>원문을 저장하지 않는다.</strong> 식별자와 해시만 남긴다 —
 * {@code docs/06-common-conventions.md} §24가 원본 Prompt 저장을 금지한다. 해시는 같은 입력에 대한
 * Prompt Risk 재평가를 건너뛰는 기준이 된다(§24.2).
 */
@Entity
@Table(name = "secured_agent_inputs")
public class SecuredAgentInput {

    /** 예: {@code INPUT-001}. */
    @Id
    @Column(name = "input_ref", nullable = false, length = 64)
    private String inputRef;

    @Column(name = "agent_run_id", nullable = false, length = 64)
    private String agentRunId;

    /** {@code sha256:...} 형식. 원문 대신 이것만 남는다. */
    @Column(name = "input_hash", nullable = false, length = 128)
    private String inputHash;

    @Column(name = "content_language", length = 16)
    private String contentLanguage;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    protected SecuredAgentInput() {
        // JPA
    }

    public SecuredAgentInput(
            String inputRef,
            String agentRunId,
            String inputHash,
            String contentLanguage,
            Instant registeredAt) {
        this.inputRef = inputRef;
        this.agentRunId = agentRunId;
        this.inputHash = inputHash;
        this.contentLanguage = contentLanguage;
        this.registeredAt = registeredAt;
    }

    public String getInputRef() {
        return inputRef;
    }

    public String getAgentRunId() {
        return agentRunId;
    }

    public String getInputHash() {
        return inputHash;
    }

    public String getContentLanguage() {
        return contentLanguage;
    }

    public Instant getRegisteredAt() {
        return registeredAt;
    }
}
