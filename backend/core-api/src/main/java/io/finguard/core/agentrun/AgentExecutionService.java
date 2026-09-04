package io.finguard.core.agentrun;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.finguard.core.domain.AgentRun;
import io.finguard.core.domain.AgentRunStatus;
import io.finguard.core.domain.AuditEvent;
import io.finguard.core.domain.AuditStatus;
import io.finguard.core.domain.ReasonCode;
import io.finguard.core.repository.AgentRunRepository;
import io.finguard.core.repository.AuditEventRepository;
import io.finguard.core.security.CoreApiAccessDeniedException;
import io.finguard.core.security.CoreApiPrincipal;
import io.finguard.core.security.CoreApiRole;

/** AgentRun 상태 원장과 그 실행에서 생성된 AuditEvent를 하나의 Public 응답으로 조립한다. */
@Service
@Transactional(readOnly = true)
public class AgentExecutionService {

    private final AgentRunRepository agentRuns;
    private final AuditEventRepository auditEvents;

    public AgentExecutionService(
            AgentRunRepository agentRuns, AuditEventRepository auditEvents) {
        this.agentRuns = agentRuns;
        this.auditEvents = auditEvents;
    }

    public AgentExecutionResponse find(String agentRunId, CoreApiPrincipal principal) {
        AgentRun run = agentRuns.findById(agentRunId).orElseThrow(AgentExecutionNotFoundException::new);
        if (principal.role() == CoreApiRole.OPERATOR
                && !run.getEmployeeId().equals(principal.employeeId())) {
            throw new CoreApiAccessDeniedException(
                    ReasonCode.EMPLOYEE_IDENTITY_MISMATCH,
                    "Credential에 묶인 Employee의 실행만 조회할 수 있습니다.");
        }

        List<AgentExecutionResponse.Attempt> attempts =
                auditEvents.findByAgentRunIdOrderByRequestedAtAscAuditEventIdAsc(agentRunId).stream()
                        .filter(event -> event.getStatus() != AuditStatus.PROCESSING)
                        .map(AgentExecutionService::toAttempt)
                        .toList();
        List<String> reasonCodes =
                attempts.stream()
                        .flatMap(attempt -> attempt.reasonCodes().stream())
                        .collect(java.util.stream.Collectors.collectingAndThen(
                                java.util.stream.Collectors.toCollection(TreeSet::new), List::copyOf));

        return new AgentExecutionResponse(
                run.getAgentRunId(), publicStatus(run.getStatus()), reasonCodes, attempts);
    }

    private static AgentRunStatus publicStatus(AgentRunStatus status) {
        return status == AgentRunStatus.CREATED ? AgentRunStatus.RUNNING : status;
    }

    private static AgentExecutionResponse.Attempt toAttempt(AuditEvent event) {
        return new AgentExecutionResponse.Attempt(
                event.getRequestId(),
                event.getRequestedTool(),
                event.getTargetConsumerId(),
                Set.copyOf(event.getRequestedData()),
                event.getDecision(),
                event.getStatus(),
                Set.copyOf(event.getReasonCodes()),
                event.getDownstreamReached(),
                event.getResponseReleased(),
                event.getScopeStatus(),
                event.getErrorLocation(),
                event.getRequestedAt(),
                event.getCompletedAt());
    }

}
