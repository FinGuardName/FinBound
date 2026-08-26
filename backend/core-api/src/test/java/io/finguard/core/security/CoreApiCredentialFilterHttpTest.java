package io.finguard.core.security;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 브라우저(Vue) → Core {@code /api/v1/**} 경로의 인증. {@code docs/04-api-contract.md} §2.
 *
 * <p>실제 서블릿 컨테이너를 지나가게 한다. {@code MockHttpServletRequest}로 필터를 직접 부르면
 * 컨테이너의 경로 매핑을 거치지 않아 경로 해석 차이로 생기는 우회를 잡지 못한다.
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
class CoreApiCredentialFilterHttpTest {

    @Container
    @ServiceConnection
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.10-alpine");

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 인증 없이 열려 있으면 누구나 임의의 {@code employeeId}로 Task Passport를 발급받는다.
     * 업무 처리는 시작조차 하지 않아야 한다.
     */
    @Test
    void rejectsAgentRunCreationWithoutACredential() {
        long before = agentRunCount();

        ResponseEntity<JsonNode> response = createAgentRun(null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("reasonCode").asText())
                .isEqualTo("CORE_API_CREDENTIAL_INVALID");
        assertThat(agentRunCount()).isEqualTo(before);
    }

    private ResponseEntity<JsonNode> createAgentRun(String bearer) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (bearer != null) {
            headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + bearer);
        }
        String body =
                """
                {
                  "employeeId": "EMP-101",
                  "consumerId": "CUST-1001",
                  "taskType": "LOAN_REVIEW",
                  "inputText": "대출 심사를 시작해 주세요"
                }
                """;
        return restTemplate.exchange(
                URI.create(base() + "/api/v1/agent-runs"),
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                JsonNode.class);
    }

    private long agentRunCount() {
        return jdbcTemplate.queryForObject("select count(*) from agent_runs", Long.class);
    }

    private String base() {
        return "http://localhost:" + port;
    }
}
