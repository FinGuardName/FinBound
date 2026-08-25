package io.finguard.agent.agentrun.api;

import java.time.OffsetDateTime;
import java.util.List;

import io.finguard.agent.agentrun.domain.AgentRun;
import io.finguard.agent.agentrun.domain.AgentRunStatus;

public record AgentRunResponse(
        String agentRunId,
        String agentId,
        String employeeId,
        String caseId,
        String passportId,
        List<String> inputRefs,
        AgentRunStatus status,
        OffsetDateTime startedAt
) {
    public static AgentRunResponse from(AgentRun agentRun) {
        return new AgentRunResponse(
                agentRun.agentRunId(),
                agentRun.agentId(),
                agentRun.employeeId(),
                agentRun.caseId(),
                agentRun.passportId(),
                agentRun.inputRefs(),
                agentRun.status(),
                agentRun.startedAt()
        );
    }
}
