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
        // audit-event.schema.json:8-16이 traceId를 필수로 둔다. 여기서 받지 않으면 저장되는
        // 모든 기록이 스키마 위반이고, 조회 응답은 필수 속성을 빠뜨린 채 나간다.
        @NotBlank @Size(max = 128) String traceId,
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
