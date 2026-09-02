package io.finguard.core.context;

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
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.JsonNode;

import io.finguard.core.security.InternalCredentialFilter;

/** {@code POST /internal/v1/context/resolve} 계약 및 오류 경계 테스트. */
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
class ContextResolveApiTest {

    private static final String REQUEST_ID = "550e8400-e29b-41d4-a716-446655440000";

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

    @Test
    void returnsAllNineStatusesAndTheNotEvaluatedPromptSnapshot() {
        RunReferences run = startAgentRun();

        ResponseEntity<JsonNode> response =
                resolve(
                        run,
                        "LOAN-AGENT-01",
                        "CUST-9999",
                        "CREDIT_SCORE_READ",
                        "CREDIT_SCORE");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("requestId").asText()).isEqualTo(REQUEST_ID);
        assertThat(body.get("references").get("employeeId").asText()).isEqualTo("EMP-101");
        JsonNode scope = body.get("scopeStatus");
        assertThat(scope.size()).isEqualTo(9);
        assertThat(scope.get("employeeAuthority").asText()).isEqualTo("OK");
        assertThat(scope.get("permissionTemplate").asText()).isEqualTo("OK");
        assertThat(scope.get("caseStatus").asText()).isEqualTo("OK");
        assertThat(scope.get("mandate").asText()).isEqualTo("OK");
        assertThat(scope.get("passportStatus").asText()).isEqualTo("OK");
        assertThat(scope.get("agentBinding").asText()).isEqualTo("OK");
        assertThat(scope.get("customerScope").asText()).isEqualTo("VIOLATION");
        assertThat(scope.get("toolScope").asText()).isEqualTo("OK");
        assertThat(scope.get("dataScope").asText()).isEqualTo("OK");
        JsonNode promptRisk = body.get("promptRiskSnapshot");
        assertThat(promptRisk.get("evaluationStatus").asText()).isEqualTo("NOT_EVALUATED");
        assertThat(promptRisk.get("detected").asBoolean()).isFalse();
        assertThat(promptRisk.get("promptRisk").decimalValue()).isZero();
        assertThat(promptRisk.get("inputHash").asText()).startsWith("sha256:");
        assertThat(promptRisk.get("modelVersion").asText()).isEqualTo("prompt-guard-5");
    }

    @Test
    void usesTheVerifiedHeaderInsteadOfTheBodyForAgentBinding() {
        RunReferences run = startAgentRun();

        ResponseEntity<JsonNode> response =
                resolve(
                        run,
                        "SPOOFED-AGENT",
                        "CUST-1001",
                        "CREDIT_SCORE_READ",
                        "CREDIT_SCORE");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("scopeStatus").get("agentBinding").asText())
                .isEqualTo("VIOLATION");
    }

    @Test
    void reportsAMissingPassportAsTaskPassportNotFound() {
        RunReferences missing = new RunReferences("RUN-MISSING", "PASS-MISSING");

        ResponseEntity<JsonNode> response =
                resolve(
                        missing,
                        "LOAN-AGENT-01",
                        "CUST-1001",
                        "CREDIT_SCORE_READ",
                        "CREDIT_SCORE");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("reasonCode").asText())
                .isEqualTo("TASK_PASSPORT_NOT_FOUND");
        assertThat(response.getBody().has("scopeStatus")).isFalse();
    }

    @Test
    void reportsAnIncompleteContextWithoutInventingViolationStatuses() {
        RunReferences run = startAgentRun();
        RunReferences missingRun = new RunReferences("RUN-MISSING", run.passportId());

        ResponseEntity<JsonNode> response =
                resolve(
                        missingRun,
                        "LOAN-AGENT-01",
                        "CUST-1001",
                        "CREDIT_SCORE_READ",
                        "CREDIT_SCORE");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("reasonCode").asText()).isEqualTo("CONTEXT_NOT_FOUND");
        assertThat(response.getBody().has("scopeStatus")).isFalse();
    }

    @Test
    void rejectsARequestThatOmitsTheToolsRequiredData() {
        RunReferences run = startAgentRun();

        ResponseEntity<JsonNode> response =
                resolve(
                        run,
                        "LOAN-AGENT-01",
                        "CUST-1001",
                        "CREDIT_SCORE_READ",
                        "INCOME");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("reasonCode").asText())
                .isEqualTo("INVALID_TOOL_REQUEST");
    }

    @Test
    void refusesContextForAnAgentRunThatIsNoLongerInFlight() {
        RunReferences run = startAgentRun();
        jdbcTemplate.update(
                "update agent_runs set status = 'COMPLETED' where agent_run_id = ?", run.agentRunId());

        ResponseEntity<JsonNode> response =
                resolve(run, "LOAN-AGENT-01", "CUST-1001", "CREDIT_SCORE_READ", "CREDIT_SCORE");

        // 끝난 실행은 더 이상 Tool Call의 근거가 될 수 없다. 상태를 보지 않으면 COMPLETED AgentRun으로도
        // 모든 Scope가 OK로 나온다.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("reasonCode").asText()).isEqualTo("CONTEXT_NOT_FOUND");
    }

    @Test
    void reportsTheWorstPromptRiskAcrossEveryInputNotJustTheLatest() {
        RunReferences run = startAgentRun();

        // 나중에 들어온 입력은 검사를 마쳤고 음성이다. 마지막 것만 보면 EVALUATED로 보고된다.
        // 그러나 앞선 입력이 아직 미검사이므로 "검사했고 음성"이라고 말할 수 없다 — docs/04 §7.
        jdbcTemplate.update(
                "insert into secured_agent_inputs"
                        + " (input_ref, agent_run_id, input_hash, content_language, registered_at)"
                        + " values (?, ?, ?, null, now())",
                "INPUT-LATER",
                run.agentRunId(),
                "sha256:later-input");
        jdbcTemplate.update(
                "insert into prompt_risk_snapshots"
                        + " (input_ref, input_hash, evaluation_status, detected, prompt_risk,"
                        + "  model_version, evaluated_at)"
                        + " values (?, ?, 'EVALUATED', false, 0.1000, 'prompt-guard-5', now())",
                "INPUT-LATER",
                "sha256:later-input");
        jdbcTemplate.update(
                "insert into agent_run_input_refs (agent_run_id, input_ref_order, input_ref)"
                        + " values (?, 1, ?)",
                run.agentRunId(),
                "INPUT-LATER");

        ResponseEntity<JsonNode> response =
                resolve(run, "LOAN-AGENT-01", "CUST-1001", "CREDIT_SCORE_READ", "CREDIT_SCORE");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("promptRiskSnapshot").get("evaluationStatus").asText())
                .isEqualTo("NOT_EVALUATED");
    }

    private RunReferences startAgentRun() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // docs/04 §2 — /api/v1/** 는 Operator Credential이 있어야 업무 처리를 시작한다.
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer test-operator-credential");
        ResponseEntity<JsonNode> response =
                restTemplate.exchange(
                        URI.create(base() + "/api/v1/agent-runs"),
                        HttpMethod.POST,
                        new HttpEntity<>(
                                """
                                {
                                  "employeeId": "EMP-101",
                                  "consumerId": "CUST-1001",
                                  "taskType": "LOAN_REVIEW",
                                  "inputText": "CUST-1001의 대출심사를 진행해줘."
                                }
                                """,
                                headers),
                        JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        return new RunReferences(
                response.getBody().get("agentRunId").asText(),
                response.getBody().get("passportId").asText());
    }

    private ResponseEntity<JsonNode> resolve(
            RunReferences run,
            String verifiedAgentHeader,
            String targetConsumerId,
            String requestedTool,
            String requestedData) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(InternalCredentialFilter.CREDENTIAL_HEADER, "test-internal-credential");
        headers.set("X-Verified-Agent-Id", verifiedAgentHeader);
        String body =
                """
                {
                  "requestId": "%s",
                  "verifiedAgentId": "LOAN-AGENT-01",
                  "agentRunId": "%s",
                  "passportId": "%s",
                  "targetConsumerId": "%s",
                  "requestedTool": "%s",
                  "requestedData": ["%s"]
                }
                """
                        .formatted(
                                REQUEST_ID,
                                run.agentRunId(),
                                run.passportId(),
                                targetConsumerId,
                                requestedTool,
                                requestedData);
        return restTemplate.exchange(
                URI.create(base() + "/internal/v1/context/resolve"),
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                JsonNode.class);
    }

    private String base() {
        return "http://localhost:" + port;
    }

    private record RunReferences(String agentRunId, String passportId) {
    }
}
