package io.finguard.core.agentrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.finguard.core.domain.AgentRun;
import io.finguard.core.domain.AgentRunStatus;
import io.finguard.core.domain.AuditEvent;
import io.finguard.core.domain.AuditStatus;
import io.finguard.core.domain.DataType;
import io.finguard.core.domain.PolicyDecision;
import io.finguard.core.domain.Tool;
import io.finguard.core.repository.AgentRunRepository;
import io.finguard.core.repository.AuditEventRepository;
import io.finguard.core.security.CoreApiAccessDeniedException;
import io.finguard.core.security.CoreApiPrincipal;
import io.finguard.core.security.CoreApiRole;

class AgentExecutionServiceTest {

    private final AgentRunRepository agentRuns = mock(AgentRunRepository.class);
    private final AuditEventRepository auditEvents = mock(AuditEventRepository.class);
    private AgentExecutionService service;

    @BeforeEach
    void setUp() {
        service = new AgentExecutionService(agentRuns, auditEvents);
    }

    @Test
    void exposesFailedRunWithoutInventingAnAuditAttempt() {
        AgentRun run = run(AgentRunStatus.FAILED);
        when(agentRuns.findById("RUN-1")).thenReturn(Optional.of(run));
        when(auditEvents.findByAgentRunIdOrderByRequestedAtAscAuditEventIdAsc("RUN-1"))
                .thenReturn(List.of());

        AgentExecutionResponse response = service.find("RUN-1", viewer());

        assertThat(response.status()).isEqualTo(AgentRunStatus.FAILED);
        assertThat(response.attempts()).isEmpty();
        assertThat(response.reasonCodes()).isEmpty();
    }

    @Test
    void exposesCreatedPreparationAsRunning() {
        when(agentRuns.findById("RUN-1")).thenReturn(Optional.of(run(AgentRunStatus.CREATED)));
        when(auditEvents.findByAgentRunIdOrderByRequestedAtAscAuditEventIdAsc("RUN-1"))
                .thenReturn(List.of());

        AgentExecutionResponse response = service.find("RUN-1", viewer());

        assertThat(response.status()).isEqualTo(AgentRunStatus.RUNNING);
        assertThat(response.attempts()).isEmpty();
    }

    @Test
    void returnsOnlyFinalizedAuditSafeAttemptsInStableOrder() {
        AgentRun run = run(AgentRunStatus.COMPLETED);
        AuditEvent processing = mock(AuditEvent.class);
        AuditEvent completed = mock(AuditEvent.class);
        when(processing.getStatus()).thenReturn(AuditStatus.PROCESSING);
        when(completed.getStatus()).thenReturn(AuditStatus.COMPLETED);
        when(completed.getRequestId()).thenReturn("REQ-1");
        when(completed.getRequestedTool()).thenReturn(Tool.CREDIT_SCORE_READ);
        when(completed.getTargetConsumerId()).thenReturn("CUST-1001");
        when(completed.getRequestedData()).thenReturn(Set.of(DataType.CREDIT_SCORE));
        when(completed.getDecision()).thenReturn(PolicyDecision.ALLOW);
        when(completed.getReasonCodes()).thenReturn(Set.of());
        when(completed.getDownstreamReached()).thenReturn(true);
        when(completed.getResponseReleased()).thenReturn(true);
        when(completed.getRequestedAt()).thenReturn(Instant.parse("2026-08-17T12:00:00Z"));
        when(completed.getCompletedAt()).thenReturn(Instant.parse("2026-08-17T12:00:01Z"));
        when(agentRuns.findById("RUN-1")).thenReturn(Optional.of(run));
        when(auditEvents.findByAgentRunIdOrderByRequestedAtAscAuditEventIdAsc("RUN-1"))
                .thenReturn(List.of(processing, completed));

        AgentExecutionResponse response = service.find("RUN-1", viewer());

        assertThat(response.attempts()).hasSize(1);
        assertThat(response.attempts().getFirst().requestId()).isEqualTo("REQ-1");
        assertThat(response.attempts().getFirst().systemOutcome())
                .isEqualTo(AuditStatus.COMPLETED);
    }

    @Test
    void operatorCannotReadAnotherEmployeesRun() {
        when(agentRuns.findById("RUN-1")).thenReturn(Optional.of(run(AgentRunStatus.RUNNING)));

        CoreApiPrincipal otherEmployee =
                new CoreApiPrincipal(CoreApiRole.OPERATOR, "EMP-OTHER");

        assertThatThrownBy(() -> service.find("RUN-1", otherEmployee))
                .isInstanceOf(CoreApiAccessDeniedException.class);
    }

    @Test
    void missingRunReturnsTheSameNotFoundShapeRegardlessOfIdentifier() {
        when(agentRuns.findById("RUN-UNKNOWN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.find("RUN-UNKNOWN", viewer()))
                .isInstanceOf(AgentExecutionNotFoundException.class)
                .hasMessage("Agent execution was not found");
    }

    private AgentRun run(AgentRunStatus status) {
        return new AgentRun(
                "RUN-1",
                "LOAN-AGENT-01",
                "EMP-101",
                "CASE-1",
                "PASS-1",
                List.of("INPUT-1"),
                status,
                Instant.parse("2026-08-17T12:00:00Z"));
    }

    private CoreApiPrincipal viewer() {
        return new CoreApiPrincipal(CoreApiRole.VIEWER, null);
    }
}
