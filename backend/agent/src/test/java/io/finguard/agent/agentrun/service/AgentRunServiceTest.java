package io.finguard.agent.agentrun.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.finguard.agent.agentrun.domain.AgentRun;
import io.finguard.agent.agentrun.domain.AgentRunStatus;
import io.finguard.agent.agentrun.domain.TaskType;
import io.finguard.agent.agentrun.port.CoreAgentRunClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AgentRunServiceTest {
    @Mock
    private CoreAgentRunClient coreAgentRunClient;

    private AgentRunService service;

    @BeforeEach
    void setUp() {
        service = new AgentRunService(coreAgentRunClient);
    }

    @Test
    void delegatesIssuanceToCoreAndUsesItsReferences() {
        CreateAgentRunCommand command = command();
        AgentRun coreIssuedRun = coreIssuedRun();
        when(coreAgentRunClient.create(command)).thenReturn(Mono.just(coreIssuedRun));

        StepVerifier.create(service.create(command))
                .assertNext(result -> assertThat(result).isSameAs(coreIssuedRun))
                .verifyComplete();

        verify(coreAgentRunClient).create(command);
    }

    @Test
    void failsClosedWhenCoreIssuanceFails() {
        CreateAgentRunCommand command = command();
        when(coreAgentRunClient.create(command))
                .thenReturn(Mono.error(new IllegalStateException("raw dependency detail")));

        StepVerifier.create(service.create(command))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(AgentRunCreationException.class);
                    assertThat(error.getMessage())
                            .isEqualTo("Core could not issue the AgentRun")
                            .doesNotContain("raw dependency detail");
                })
                .verify();

        verify(coreAgentRunClient).create(command);
    }

    private CreateAgentRunCommand command() {
        return new CreateAgentRunCommand(
                "EMP-101",
                "CUST-1001",
                TaskType.LOAN_REVIEW,
                "대출심사를 진행해줘."
        );
    }

    private AgentRun coreIssuedRun() {
        return new AgentRun(
                "RUN-001",
                "LOAN-AGENT-01",
                "EMP-101",
                "LOAN-2026-001",
                "PASS-001",
                List.of("INPUT-001"),
                AgentRunStatus.RUNNING,
                Instant.parse("2026-08-25T01:00:00Z")
        );
    }
}
