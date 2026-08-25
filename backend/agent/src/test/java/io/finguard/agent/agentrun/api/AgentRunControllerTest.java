package io.finguard.agent.agentrun.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import io.finguard.agent.agentrun.domain.AgentRun;
import io.finguard.agent.agentrun.domain.AgentRunContext;
import io.finguard.agent.agentrun.domain.SecuredInputReference;
import io.finguard.agent.agentrun.service.AgentRunCreationException;
import io.finguard.agent.agentrun.service.AgentRunService;

@WebFluxTest(AgentRunController.class)
@Import(AgentRunExceptionHandler.class)
class AgentRunControllerTest {
    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private AgentRunService agentRunService;

    @Test
    void createsRunningAgentRunWithoutRawInputOrHash() {
        when(agentRunService.create(any())).thenReturn(runningAgentRun());

        webTestClient.post()
                .uri("/api/v1/agent-runs")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "employeeId": "EMP-101",
                          "consumerId": "CUST-1001",
                          "taskType": "LOAN_REVIEW",
                          "inputText": "CUST-1001의 대출심사를 진행해줘."
                        }
                        """)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.agentRunId").isEqualTo("RUN-001")
                .jsonPath("$.agentId").isEqualTo("LOAN-AGENT-01")
                .jsonPath("$.employeeId").isEqualTo("EMP-101")
                .jsonPath("$.caseId").isEqualTo("LOAN-2026-001")
                .jsonPath("$.passportId").isEqualTo("PASS-001")
                .jsonPath("$.inputRefs[0]").isEqualTo("INPUT-001")
                .jsonPath("$.status").isEqualTo("RUNNING")
                .jsonPath("$.startedAt").isEqualTo("2026-08-25T10:00:00+09:00")
                .jsonPath("$.inputText").doesNotExist()
                .jsonPath("$.inputHash").doesNotExist();
    }

    @Test
    void rejectsInvalidRequest() {
        webTestClient.post()
                .uri("/api/v1/agent-runs")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "employeeId": "",
                          "consumerId": "CUST-1001",
                          "taskType": "LOAN_REVIEW",
                          "inputText": ""
                        }
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("INVALID_AGENT_RUN_REQUEST");
    }

    @Test
    void returnsFailClosedErrorWhenContextPreparationFails() {
        when(agentRunService.create(any())).thenThrow(
                new AgentRunCreationException(new IllegalStateException("raw dependency detail"))
        );

        webTestClient.post()
                .uri("/api/v1/agent-runs")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "employeeId": "EMP-101",
                          "consumerId": "CUST-1001",
                          "taskType": "LOAN_REVIEW",
                          "inputText": "CUST-1001의 대출심사를 진행해줘."
                        }
                        """)
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("CONTEXT_SERVICE_UNAVAILABLE")
                .jsonPath("$.message").isEqualTo("AgentRun context could not be prepared");
    }

    private AgentRun runningAgentRun() {
        AgentRun agentRun = AgentRun.created("RUN-001", "LOAN-AGENT-01", "EMP-101");
        agentRun.start(
                new AgentRunContext("LOAN-2026-001", "PASS-001"),
                new SecuredInputReference("INPUT-001", "sha256:internal-only"),
                OffsetDateTime.parse("2026-08-25T10:00:00+09:00")
        );
        return agentRun;
    }
}
