package io.finguard.core.audit;

import java.time.Instant;

import io.finguard.core.domain.AuditStatus;
import io.finguard.core.domain.Tool;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 인증 성공 직후 생성하는 PROCESSING Business Audit 요청. */
public record AuditCreateRequest(
        @NotBlank @Size(max = 64) String requestId,
        @Size(max = 128) String traceId,
        @NotBlank @Size(max = 64) String agentRunId,
        @NotBlank @Size(max = 64) String verifiedAgentId,
        @Size(max = 64) String caseId,
        @Size(max = 64) String targetConsumerId,
        Tool requestedTool,
        @NotNull AuditStatus status,
        @NotNull Instant requestedAt) {

    @AssertTrue(message = "status must be PROCESSING")
    public boolean isProcessing() {
        return status == null || status == AuditStatus.PROCESSING;
    }
}
