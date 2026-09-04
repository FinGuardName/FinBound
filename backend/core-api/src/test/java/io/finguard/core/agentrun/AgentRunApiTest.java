package io.finguard.core.agentrun;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * {@code POST /api/v1/agent-runs} — {@code docs/04-api-contract.md} §3.
 *
 * <p>응답 본문은 §4.2 AgentRun 형태를 따른다. 권한 내용(allowedTools/allowedData)은 여기 싣지 않는다 —
 * 그건 Task Passport(§4.1)의 것이고, 비교 화면용 엔드포인트가 §15에 따로 있다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "finguard.internal.credential=test-internal-credential",
            "finguard.api.viewer-credential=test-viewer-credential",
            "finguard.api.operator-credential=test-operator-credential",
            "finguard.api.operator-employee-id=EMP-101",
        })
@ActiveProfiles("local")
@Testcontainers
class AgentRunApiTest {

    @Container
    @ServiceConnection
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.10-alpine");

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void startsARunAndReturnsTheIssuedPassportReference() {
        ResponseEntity<JsonNode> response =
                post(
                        """
                        {
                          "employeeId": "EMP-101",
                          "consumerId": "CUST-1001",
                          "taskType": "LOAN_REVIEW",
                          "inputText": "CUST-1001의 대출심사를 진행해줘."
                        }
                        """);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("agentRunId").asText()).startsWith("RUN-");
        assertThat(body.get("passportId").asText()).startsWith("PASS-");
        assertThat(body.get("employeeId").asText()).isEqualTo("EMP-101");
        assertThat(body.get("status").asText()).isEqualTo("RUNNING");
        assertThat(body.get("inputRefs")).hasSize(1);
    }

    @Test
    void neverEchoesTheRawInputBack() {
        String rawInput = "이 문장은 응답에 실려 나오면 안 된다";

        ResponseEntity<String> response =
                restTemplate.exchange(
                        URI.create(base() + "/api/v1/agent-runs"),
                        org.springframework.http.HttpMethod.POST,
                        jsonEntity(
                                """
                                {
                                  "employeeId": "EMP-101",
                                  "consumerId": "CUST-1001",
                                  "taskType": "LOAN_REVIEW",
                                  "inputText": "%s"
                                }
                                """
                                        .formatted(rawInput)),
                        String.class);

        // docs/06 §24 — 원본 Prompt는 저장도 로그도 하지 않는다. 응답으로 되돌려주지도 않는다.
        assertThat(response.getBody()).doesNotContain(rawInput);
    }

    @Test
    void rejectsAnInputLongerThanTheDetectorAccepts() {
        // ai-risk 는 4096자를 넘으면 422로 거부한다(ai-risk/app/schemas/prompt.py:33).
        // Core 가 먼저 막지 않으면 평가되지 못한 입력으로 실행이 만들어지고, 그 스냅샷은
        // NOT_EVALUATED 로 남아 Gateway 가 모든 Tool Call 을 막는다 — 성공했지만 반드시 막힐 실행이다.
        ResponseEntity<String> response =
                restTemplate.exchange(
                        URI.create(base() + "/api/v1/agent-runs"),
                        org.springframework.http.HttpMethod.POST,
                        jsonEntity(
                                """
                                {
                                  "employeeId": "EMP-101",
                                  "consumerId": "CUST-1001",
                                  "taskType": "LOAN_REVIEW",
                                  "inputText": "%s"
                                }
                                """
                                        .formatted("가".repeat(4097))),
                        String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void refusesWhenTheConsumerGaveNoMandate() {
        ResponseEntity<JsonNode> response =
                post(
                        """
                        {
                          "employeeId": "EMP-101",
                          "consumerId": "CUST-9999",
                          "taskType": "LOAN_REVIEW",
                          "inputText": "대출심사 진행"
                        }
                        """);

        // 요청 형식은 멀쩡한데 현재 권한 상태로는 만들 수 없다.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("reasonCode").asText()).isEqualTo("MANDATE_NOT_FOUND");
    }

    @Test
    void rejectsARequestMissingRequiredFields() {
        ResponseEntity<JsonNode> response = post("{\"employeeId\": \"EMP-101\"}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("reasonCode").asText()).isEqualTo("INVALID_TOOL_REQUEST");
    }

    private ResponseEntity<JsonNode> post(String json) {
        return restTemplate.exchange(
                URI.create(base() + "/api/v1/agent-runs"),
                org.springframework.http.HttpMethod.POST,
                jsonEntity(json),
                JsonNode.class);
    }

    private org.springframework.http.HttpEntity<String> jsonEntity(String json) {
        var headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // docs/04 §2 — /api/v1/** 는 Operator Credential이 있어야 업무 처리를 시작한다.
        headers.set(
                org.springframework.http.HttpHeaders.AUTHORIZATION,
                "Bearer test-operator-credential");
        return new org.springframework.http.HttpEntity<>(json, headers);
    }

    private String base() {
        return "http://localhost:" + port;
    }
}
