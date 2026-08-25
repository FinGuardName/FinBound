package io.finguard.agent.agentrun.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.finguard.agent.agentrun.domain.AgentRun;
import io.finguard.agent.agentrun.domain.AgentRunContext;
import io.finguard.agent.agentrun.domain.AgentRunStatus;
import io.finguard.agent.agentrun.domain.SecuredInputReference;
import io.finguard.agent.agentrun.domain.TaskType;
import io.finguard.agent.agentrun.port.AgentRunContextProvider;
import io.finguard.agent.agentrun.port.AgentRunRepository;
import io.finguard.agent.agentrun.port.SecuredInputStore;

@ExtendWith(MockitoExtension.class)
class AgentRunServiceTest {
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-08-25T01:00:00Z"),
            ZoneOffset.ofHours(9)
    );

    @Mock
    private AgentRunContextProvider contextProvider;
    @Mock
    private SecuredInputStore securedInputStore;
    @Mock
    private AgentRunRepository agentRunRepository;

    private AgentRunService service;

    @BeforeEach
    void setUp() {
        service = new AgentRunService(
                contextProvider,
                securedInputStore,
                agentRunRepository,
                "LOAN-AGENT-01",
                FIXED_CLOCK
        );
    }

    @Test
    void createsRunningAgentRunWithContextAndInputReference() {
        makeRepositoryReturnSavedRun();
        when(contextProvider.prepare("EMP-101", "CUST-1001", TaskType.LOAN_REVIEW))
                .thenReturn(new AgentRunContext("LOAN-2026-001", "PASS-001"));
        when(securedInputStore.store("대출심사를 진행해줘."))
                .thenReturn(new SecuredInputReference("INPUT-001", "sha256:internal-only"));

        AgentRun result = service.create(command());

        assertThat(result.agentRunId()).startsWith("RUN-");
        assertThat(result.status()).isEqualTo(AgentRunStatus.RUNNING);
        assertThat(result.caseId()).isEqualTo("LOAN-2026-001");
        assertThat(result.passportId()).isEqualTo("PASS-001");
        assertThat(result.inputRefs()).containsExactly("INPUT-001");
        assertThat(result.startedAt()).isAtSameInstantAs(
                java.time.OffsetDateTime.ofInstant(FIXED_CLOCK.instant(), ZoneOffset.ofHours(9))
        );
    }

    @Test
    void marksRunFailedAndDoesNotStoreInputWhenContextPreparationFails() {
        makeRepositoryReturnSavedRun();
        when(contextProvider.prepare("EMP-101", "CUST-1001", TaskType.LOAN_REVIEW))
                .thenThrow(new IllegalStateException("Core unavailable"));

        assertThatThrownBy(() -> service.create(command()))
                .isInstanceOf(AgentRunCreationException.class)
                .hasMessageNotContaining("대출심사를 진행해줘.");

        ArgumentCaptor<AgentRun> runCaptor = ArgumentCaptor.forClass(AgentRun.class);
        verify(agentRunRepository, org.mockito.Mockito.times(2)).save(runCaptor.capture());
        assertThat(runCaptor.getValue().status()).isEqualTo(AgentRunStatus.FAILED);
        verify(securedInputStore, never()).store(any());
    }

    @Test
    void marksRunFailedWhenSecuredInputStorageFails() {
        makeRepositoryReturnSavedRun();
        when(contextProvider.prepare("EMP-101", "CUST-1001", TaskType.LOAN_REVIEW))
                .thenReturn(new AgentRunContext("LOAN-2026-001", "PASS-001"));
        when(securedInputStore.store("대출심사를 진행해줘."))
                .thenThrow(new IllegalStateException("Input store unavailable"));

        assertThatThrownBy(() -> service.create(command()))
                .isInstanceOf(AgentRunCreationException.class);

        ArgumentCaptor<AgentRun> runCaptor = ArgumentCaptor.forClass(AgentRun.class);
        verify(agentRunRepository, org.mockito.Mockito.times(2)).save(runCaptor.capture());
        assertThat(runCaptor.getValue().status()).isEqualTo(AgentRunStatus.FAILED);
    }

    @Test
    void completesAndFailsExistingRuns() {
        makeRepositoryReturnSavedRun();
        AgentRun completedRun = runningAgentRun("RUN-001");
        AgentRun failedRun = runningAgentRun("RUN-002");
        when(agentRunRepository.findById("RUN-001")).thenReturn(java.util.Optional.of(completedRun));
        when(agentRunRepository.findById("RUN-002")).thenReturn(java.util.Optional.of(failedRun));

        assertThat(service.complete("RUN-001").status()).isEqualTo(AgentRunStatus.COMPLETED);
        assertThat(service.fail("RUN-002").status()).isEqualTo(AgentRunStatus.FAILED);
    }

    @Test
    void rejectsUnknownAgentRun() {
        when(agentRunRepository.findById("RUN-404")).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.complete("RUN-404"))
                .isInstanceOf(AgentRunNotFoundException.class)
                .hasMessageContaining("RUN-404");
    }

    private void makeRepositoryReturnSavedRun() {
        when(agentRunRepository.save(any(AgentRun.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private CreateAgentRunCommand command() {
        return new CreateAgentRunCommand(
                "EMP-101",
                "CUST-1001",
                TaskType.LOAN_REVIEW,
                "대출심사를 진행해줘."
        );
    }

    private AgentRun runningAgentRun(String agentRunId) {
        AgentRun agentRun = AgentRun.created(agentRunId, "LOAN-AGENT-01", "EMP-101");
        agentRun.start(
                new AgentRunContext("LOAN-2026-001", "PASS-001"),
                new SecuredInputReference("INPUT-001", "sha256:internal-only"),
                java.time.OffsetDateTime.now(FIXED_CLOCK)
        );
        return agentRun;
    }
}
