package io.finguard.agent.agentrun.domain;

import java.time.OffsetDateTime;
import java.util.List;

public class AgentRun {
    private final String agentRunId;
    private final String agentId;
    private final String employeeId;
    private String caseId;
    private String passportId;
    private List<String> inputRefs = List.of();
    private AgentRunStatus status = AgentRunStatus.CREATED;
    private OffsetDateTime startedAt;
    private OffsetDateTime completedAt;

    private AgentRun(String agentRunId, String agentId, String employeeId) {
        this.agentRunId = agentRunId;
        this.agentId = agentId;
        this.employeeId = employeeId;
    }

    public static AgentRun created(String agentRunId, String agentId, String employeeId) {
        return new AgentRun(agentRunId, agentId, employeeId);
    }

    public void start(
            AgentRunContext context,
            SecuredInputReference inputReference,
            OffsetDateTime startTime
    ) {
        requireStatus(AgentRunStatus.CREATED);
        caseId = context.caseId();
        passportId = context.passportId();
        inputRefs = List.of(inputReference.inputRef());
        startedAt = startTime;
        status = AgentRunStatus.RUNNING;
    }

    public void complete(OffsetDateTime completionTime) {
        requireStatus(AgentRunStatus.RUNNING);
        completedAt = completionTime;
        status = AgentRunStatus.COMPLETED;
    }

    public void fail(OffsetDateTime failureTime) {
        if (status != AgentRunStatus.CREATED && status != AgentRunStatus.RUNNING) {
            throw new IllegalStateException("AgentRun cannot fail from status " + status);
        }
        completedAt = failureTime;
        status = AgentRunStatus.FAILED;
    }

    private void requireStatus(AgentRunStatus expectedStatus) {
        if (status != expectedStatus) {
            throw new IllegalStateException(
                    "AgentRun status must be " + expectedStatus + ", but was " + status
            );
        }
    }

    public String agentRunId() {
        return agentRunId;
    }

    public String agentId() {
        return agentId;
    }

    public String employeeId() {
        return employeeId;
    }

    public String caseId() {
        return caseId;
    }

    public String passportId() {
        return passportId;
    }

    public List<String> inputRefs() {
        return inputRefs;
    }

    public AgentRunStatus status() {
        return status;
    }

    public OffsetDateTime startedAt() {
        return startedAt;
    }

    public OffsetDateTime completedAt() {
        return completedAt;
    }
}
