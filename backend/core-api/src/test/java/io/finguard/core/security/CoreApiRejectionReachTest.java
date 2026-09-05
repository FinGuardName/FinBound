package io.finguard.core.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

import java.net.URI;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.JsonNode;

import io.finguard.core.agentrun.AgentRunService;

/**
 * 거부된 요청이 <strong>업무 계층에 도달조차 하지 않는다</strong>는 것을 확인한다.
 * {@code docs/04-api-contract.md} §2 — "거부된 요청은 Controller의 업무 처리와 Persistence·Prompt Risk
 * 등 후속 호출에 도달하지 않아야 한다."
 *
 * <p>저장된 행 수를 세는 것으로는 이걸 증명하지 못한다. 업무 로직이 실행됐다가 트랜잭션이 롤백돼도
 * 행 수는 그대로이기 때문이다. 실행 자체가 없었다는 것은 호출 횟수로만 말할 수 있다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "finguard.internal.credential=test-internal-credential",
            "finguard.api.viewer-credential=test-viewer-credential",
            "finguard.api.operator-credential=test-operator-credential",
            "finguard.api.operator-employee-id=EMP-101",
        })
@Testcontainers
class CoreApiRejectionReachTest {

    @Container
    @ServiceConnection
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.10-alpine");

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @MockitoBean
    private AgentRunService agentRunService;

    @Test
    void requestWithoutACredentialNeverReachesTheService() {
        ResponseEntity<JsonNode> response = createAgentRun(null, "EMP-101");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(agentRunService);
    }

    @Test
    void viewerCredentialNeverReachesTheService() {
        ResponseEntity<JsonNode> response = createAgentRun("test-viewer-credential", "EMP-101");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verifyNoInteractions(agentRunService);
    }

    @Test
    void mismatchedEmployeeNeverReachesTheService() {
        ResponseEntity<JsonNode> response = createAgentRun("test-operator-credential", "EMP-999");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verifyNoInteractions(agentRunService);
    }

    /** Credential 원문은 응답 본문 어디에도 실려 나오지 않는다 — {@code docs/06} §26. */
    @Test
    void neverEchoesTheRejectedCredential() {
        String presented = "rejected-credential-must-not-be-echoed";

        ResponseEntity<String> response =
                restTemplate.exchange(
                        URI.create(base() + "/api/v1/agent-runs"),
                        HttpMethod.POST,
                        new HttpEntity<>(body("EMP-101"), jsonHeaders(presented)),
                        String.class);

        assertThat(response.getBody()).doesNotContain(presented);
    }

    private ResponseEntity<JsonNode> createAgentRun(String bearer, String employeeId) {
        return restTemplate.exchange(
                URI.create(base() + "/api/v1/agent-runs"),
                HttpMethod.POST,
                new HttpEntity<>(body(employeeId), jsonHeaders(bearer)),
                JsonNode.class);
    }

    private HttpHeaders jsonHeaders(String bearer) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (bearer != null) {
            headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + bearer);
        }
        return headers;
    }

    private String body(String employeeId) {
        return """
                {
                  "employeeId": "%s",
                  "consumerId": "CUST-1001",
                  "taskType": "LOAN_REVIEW",
                  "inputText": "대출 심사를 시작해 주세요"
                }
                """
                .formatted(employeeId);
    }

    private String base() {
        return "http://localhost:" + port;
    }
}
