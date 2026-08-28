package io.finguard.agent.agentrun.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;

import io.finguard.agent.agentrun.domain.AgentRunStatus;
import io.finguard.agent.agentrun.domain.TaskType;
import io.finguard.agent.agentrun.service.CreateAgentRunCommand;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import reactor.test.StepVerifier;

class CoreAgentRunHttpClientTest {
    private final AtomicReference<String> requestPath = new AtomicReference<>();
    private final AtomicReference<String> requestBody = new AtomicReference<>();

    private DisposableServer coreServer;
    private CoreAgentRunHttpClient client;

    @BeforeEach
    void setUp() {
        coreServer = HttpServer.create()
                .port(0)
                .handle((request, response) -> {
                    requestPath.set(request.uri());
                    return request.receive()
                            .aggregate()
                            .asString()
                            .flatMap(body -> {
                                requestBody.set(body);
                                response.status(201);
                                response.header(HttpHeaders.CONTENT_TYPE, "application/json");
                                return response.sendString(Mono.just(successResponse())).then();
                            });
                })
                .bindNow();
        WebClient webClient = WebClient.create(
                "http://localhost:" + coreServer.port()
        );
        client = new CoreAgentRunHttpClient(webClient);
    }

    @AfterEach
    void tearDown() {
        coreServer.disposeNow();
    }

    @Test
    void callsTheCoreOwnedEndpointAndMapsTheIssuedRun() {
        CreateAgentRunCommand command = new CreateAgentRunCommand(
                "EMP-101",
                "CUST-1001",
                TaskType.LOAN_REVIEW,
                "CUST-1001의 대출심사를 진행해줘."
        );

        StepVerifier.create(client.create(command))
                .assertNext(agentRun -> {
                    assertThat(agentRun.agentRunId()).isEqualTo("RUN-001");
                    assertThat(agentRun.caseId()).isEqualTo("LOAN-2026-001");
                    assertThat(agentRun.passportId()).isEqualTo("PASS-001");
                    assertThat(agentRun.inputRefs()).containsExactly("INPUT-001");
                    assertThat(agentRun.status()).isEqualTo(AgentRunStatus.RUNNING);
                })
                .verifyComplete();

        assertThat(requestPath).hasValue("/api/v1/agent-runs");
        assertThat(requestBody.get())
                .contains("\"employeeId\":\"EMP-101\"")
                .contains("\"consumerId\":\"CUST-1001\"")
                .contains("\"taskType\":\"LOAN_REVIEW\"")
                .contains("\"inputText\":\"CUST-1001의 대출심사를 진행해줘.\"");
    }

    private String successResponse() {
        return """
                {
                  "agentRunId": "RUN-001",
                  "agentId": "LOAN-AGENT-01",
                  "employeeId": "EMP-101",
                  "caseId": "LOAN-2026-001",
                  "passportId": "PASS-001",
                  "inputRefs": ["INPUT-001"],
                  "status": "RUNNING",
                  "startedAt": "2026-08-25T01:00:00Z"
                }
                """;
    }
}
