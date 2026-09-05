package io.finguard.core.agentrun;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.finguard.core.domain.AgentRunStatus;
import io.finguard.core.domain.AuditScopeStatus;
import io.finguard.core.domain.AuditStatus;
import io.finguard.core.domain.DataType;
import io.finguard.core.domain.PolicyDecision;
import io.finguard.core.domain.Tool;

/** Frontend가 소비하는 AgentRun 실행 상태. 원본 Prompt와 금융 응답은 포함하지 않는다. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AgentExecutionResponse(
        String agentRunId,
        AgentRunStatus status,
        List<String> reasonCodes,
        List<Attempt> attempts) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Attempt(
            String requestId,
            Tool requestedTool,
            String targetConsumerId,
            Set<DataType> requestedData,
            PolicyDecision decision,
            AuditStatus systemOutcome,
            Set<String> reasonCodes,
            Boolean downstreamReached,
            Boolean responseReleased,
            AuditScopeStatus scopeStatus,
            String errorLocation,
            Instant requestedAt,
            Instant completedAt) {
    }
}
