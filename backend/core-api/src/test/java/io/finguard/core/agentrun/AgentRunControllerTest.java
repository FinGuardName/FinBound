package io.finguard.core.agentrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import io.finguard.core.domain.AgentRunStatus;
import io.finguard.core.domain.TaskType;
import io.finguard.core.security.CoreApiAccessDeniedException;
import io.finguard.core.security.CoreApiPrincipal;
import io.finguard.core.security.CoreApiRole;

@ExtendWith(MockitoExtension.class)
class AgentRunControllerTest {
    @Mock
    private AgentRunService agentRunService;

    @Mock
    private AgentSimulatorClient agentSimulatorClient;

    private AgentRunController controller;

    @BeforeEach
    void setUp() {
        controller = new AgentRunController(agentRunService, agentSimulatorClient);
    }

    @Test
    void startsSimulatorWithOnlyCoreIssuedReferences() {
        AgentRunCreateRequest request = request("EMP-101");
        when(agentRunService.start(
                request.employeeId(), request.consumerId(), request.taskType(), request.inputText()))
                .thenReturn(started());

        var response = controller.create(request, operator());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verify(agentSimulatorClient).simulate("RUN-CORE-001", "PASS-CORE-001");
    }

    @Test
    void marksRunFailedAndNeverConvertsAgentTimeoutToSuccess() {
        AgentRunCreateRequest request = request("EMP-101");
        when(agentRunService.start(
                request.employeeId(), request.consumerId(), request.taskType(), request.inputText()))
                .thenReturn(started());
        doThrow(new AgentSimulatorCallException("AGENT_SIMULATOR_TIMEOUT"))
                .when(agentSimulatorClient).simulate("RUN-CORE-001", "PASS-CORE-001");

        assertThatThrownBy(() -> controller.create(request, operator()))
                .isInstanceOf(AgentSimulatorCallException.class)
                .extracting("errorCode")
                .isEqualTo("AGENT_SIMULATOR_TIMEOUT");
        verify(agentRunService).fail("RUN-CORE-001");
    }

    @Test
    void rejectsEmployeeMismatchBeforeCreationOrAgentCall() {
        assertThatThrownBy(() -> controller.create(request("EMP-999"), operator()))
                .isInstanceOf(CoreApiAccessDeniedException.class);

        verifyNoInteractions(agentRunService, agentSimulatorClient);
    }

    private AgentRunCreateRequest request(String employeeId) {
        return new AgentRunCreateRequest(
                employeeId, "CUST-1001", TaskType.LOAN_REVIEW, "대출심사를 진행해줘.");
    }

    private CoreApiPrincipal operator() {
        return new CoreApiPrincipal(CoreApiRole.OPERATOR, "EMP-101");
    }

    private AgentRunStarted started() {
        return new AgentRunStarted(
                "RUN-CORE-001", "LOAN-AGENT-01", "EMP-101", "LOAN-2026-001",
                "PASS-CORE-001", "CUST-1001", List.of("INPUT-001"), AgentRunStatus.RUNNING,
                Instant.parse("2026-09-01T01:00:00Z"), Set.of(), Set.of());
    }
}
