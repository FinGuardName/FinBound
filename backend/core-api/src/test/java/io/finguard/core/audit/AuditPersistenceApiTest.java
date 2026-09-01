package io.finguard.core.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

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

import io.finguard.core.security.InternalCredentialFilter;

/** Business Audit와 인증 실패 SecurityAuthEvent의 실제 HTTP/PostgreSQL 경계. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "finguard.internal.credential=test-internal-credential",
            "finguard.api.viewer-credential=test-viewer-credential",
            "finguard.api.operator-credential=test-operator-credential",
            "finguard.api.operator-employee-id=EMP-101",
        })
@Testcontainers
class AuditPersistenceApiTest {

    private static final String VERIFIED_AGENT_HEADER = "X-Verified-Agent-Id";
    private static final String REQUESTED_AT = "2026-08-25T12:00:00Z";
    private static final String COMPLETED_AT = "2026-08-25T12:00:01Z";
    private static final String HASH_FINGERPRINT =
            "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

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
    void createsAProcessingAuditUsingTheVerifiedGatewayHeader() {
        String requestId = requestId();

        ResponseEntity<JsonNode> response =
                createAudit(requestId, "SPOOFED-BODY-AGENT", "LOAN-AGENT-01", true);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("auditEventId").asText()).startsWith("AUD-");
        assertThat(body.get("requestId").asText()).isEqualTo(requestId);
        assertThat(body.get("agentId").asText()).isEqualTo("LOAN-AGENT-01");
        assertThat(body.get("status").asText()).isEqualTo("PROCESSING");
        assertThat(body.get("decision").isNull()).isTrue();

        String storedAgent =
                jdbcTemplate.queryForObject(
                        "select agent_id from audit_events where request_id = ?",
                        String.class,
                        requestId);
        assertThat(storedAgent).isEqualTo("LOAN-AGENT-01");
    }

    /**
     * Behavior History(§9)는 {@code caseId}·{@code targetConsumerId}·{@code tool}을 함께 돌려준다.
     * 생성 경로가 이 Context를 받지 않으면 컬럼이 영영 비어 이력이 어떤 업무였는지 말하지 못한다.
     */
    @Test
    void persistsToolAndCaseContextGivenAtCreation() {
        String requestId = requestId();

        ResponseEntity<JsonNode> response =
                createAudit(requestId, "LOAN-AGENT-01", "LOAN-AGENT-01", true);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> stored =
                jdbcTemplate.queryForMap(
                        "select case_id, target_consumer_id, requested_tool from audit_events"
                                + " where request_id = ?",
                        requestId);
        assertThat(stored.get("case_id")).isEqualTo("LOAN-2026-001");
        assertThat(stored.get("target_consumer_id")).isEqualTo("CUST-1001");
        assertThat(stored.get("requested_tool")).isEqualTo("CREDIT_SCORE_READ");
    }

    @Test
    void doesNotCreateBusinessAuditWithoutVerifiedIdentity() {
        String requestId = requestId();

        ResponseEntity<JsonNode> response =
                createAudit(requestId, "LOAN-AGENT-01", null, true);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(auditCount(requestId)).isZero();
    }

    @Test
    void rejectsDuplicateRequestIdSoOnlyTheWinnerCanProceed() {
        String requestId = requestId();
        ResponseEntity<JsonNode> first =
                createAudit(requestId, "LOAN-AGENT-01", "LOAN-AGENT-01", true);

        ResponseEntity<JsonNode> duplicate =
                createAudit(requestId, "LOAN-AGENT-01", "LOAN-AGENT-01", true);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicate.getBody()).isNotNull();
        assertThat(duplicate.getBody().get("reasonCode").asText()).isEqualTo("DUPLICATE_REQUEST");
        assertThat(auditCount(requestId)).isEqualTo(1);
    }

    @Test
    void rejectsExecutionMeasurementsOnABlockedOutcome() {
        String requestId = requestId();
        createAudit(requestId, "LOAN-AGENT-01", "LOAN-AGENT-01", true);

        // BLOCK은 downstream에 닿지 않았으므로 실행 측정값이 존재할 수 없다.
        // contracts/audit/execution-outcome.schema.json과 audit-event.schema.json이
        // 둘 다 이 셋을 BLOCK에서 금지한다. 받아서 저장하면 스키마 위반 기록이 남는다.
        ResponseEntity<JsonNode> response =
                updateOutcome(
                        requestId,
                        "LOAN-AGENT-01",
                        """
                        {
                          "decision": "BLOCK",
                          "systemOutcome": "COMPLETED",
                          "reasonCodes": ["CASE_SCOPE_VIOLATION"],
                          "downstreamReached": false,
                          "responseReleased": false,
                          "success": false,
                          "recordsRead": 0,
                          "latencyMs": 18,
                          "completedAt": "%s"
                        }
                        """
                                .formatted(COMPLETED_AT));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "select status from audit_events where request_id = ?",
                                String.class,
                                requestId))
                .isEqualTo("PROCESSING");
    }

    @Test
    void completesBlockedOutcomeWithoutDownstreamReachability() {
        String requestId = requestId();
        createAudit(requestId, "LOAN-AGENT-01", "LOAN-AGENT-01", true);

        ResponseEntity<JsonNode> response =
                updateOutcome(
                        requestId,
                        "LOAN-AGENT-01",
                        """
                        {
                          "decision": "BLOCK",
                          "systemOutcome": "COMPLETED",
                          "reasonCodes": ["CASE_SCOPE_VIOLATION"],
                          "downstreamReached": false,
                          "responseReleased": false,
                          "behaviorRisk": 0.21,
                          "policyVersion": "loan-review-policy-1",
                          "completedAt": "%s"
                        }
                        """
                                .formatted(COMPLETED_AT));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status").asText()).isEqualTo("COMPLETED");
        assertThat(response.getBody().get("decision").asText()).isEqualTo("BLOCK");
        assertThat(response.getBody().get("downstreamReached").asBoolean()).isFalse();
        assertThat(
                        jdbcTemplate.queryForObject(
                                "select downstream_reached from audit_events where request_id = ?",
                                Boolean.class,
                                requestId))
                .isFalse();
        assertThat(
                        jdbcTemplate.queryForList(
                                "select reason_code from audit_event_reason_codes"
                                        + " where audit_event_id ="
                                        + " (select audit_event_id from audit_events where request_id = ?)",
                                String.class,
                                requestId))
                .containsExactly("CASE_SCOPE_VIOLATION");
    }

    @Test
    void completesAllowedOutcomeAfterDownstreamAndResponseRelease() {
        String requestId = requestId();
        createAudit(requestId, "LOAN-AGENT-01", "LOAN-AGENT-01", true);

        ResponseEntity<JsonNode> response =
                updateOutcome(
                        requestId,
                        "LOAN-AGENT-01",
                        """
                        {
                          "decision": "ALLOW",
                          "systemOutcome": "COMPLETED",
                          "reasonCodes": [],
                          "downstreamReached": true,
                          "responseReleased": true,
                          "success": true,
                          "behaviorRisk": 0.08,
                          "policyVersion": "loan-review-policy-1",
                          "completedAt": "%s"
                        }
                        """
                                .formatted(COMPLETED_AT));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("decision").asText()).isEqualTo("ALLOW");
        assertThat(response.getBody().get("status").asText()).isEqualTo("COMPLETED");
        assertThat(response.getBody().get("downstreamReached").asBoolean()).isTrue();
        assertThat(response.getBody().get("responseReleased").asBoolean()).isTrue();
    }

    /**
     * Behavior History(§9)가 {@code success}·{@code latencyMs}를 싣기로 돼 있는데, 완료 경로가 그
     * 값을 받지 않으면 이력이 전부 null이 되어 AI Risk에 넘길 근거가 사라진다.
     */
    @Test
    void persistsExecutionMeasurementsFromTheOutcomeRequest() {
        String requestId = requestId();
        createAudit(requestId, "LOAN-AGENT-01", "LOAN-AGENT-01", true);

        ResponseEntity<JsonNode> response =
                updateOutcome(
                        requestId,
                        "LOAN-AGENT-01",
                        """
                        {
                          "decision": "ALLOW",
                          "systemOutcome": "COMPLETED",
                          "reasonCodes": [],
                          "downstreamReached": true,
                          "responseReleased": true,
                          "success": true,
                          "recordsRead": 1,
                          "latencyMs": 120,
                          "behaviorRisk": 0.08,
                          "policyVersion": "loan-review-policy-1",
                          "completedAt": "%s"
                        }
                        """
                                .formatted(COMPLETED_AT));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("success").asBoolean()).isTrue();
        assertThat(response.getBody().get("recordsRead").asInt()).isEqualTo(1);
        assertThat(response.getBody().get("latencyMs").asLong()).isEqualTo(120L);

        Map<String, Object> stored =
                jdbcTemplate.queryForMap(
                        "select success, records_read, latency_ms from audit_events"
                                + " where request_id = ?",
                        requestId);
        assertThat(stored.get("success")).isEqualTo(true);
        assertThat(stored.get("records_read")).isEqualTo(1);
        assertThat(stored.get("latency_ms")).isEqualTo(120L);
    }

    @Test
    void recordsSystemErrorSeparatelyFromTheAllowDecision() {
        String requestId = requestId();
        createAudit(requestId, "LOAN-AGENT-01", "LOAN-AGENT-01", true);

        ResponseEntity<JsonNode> response =
                updateOutcome(
                        requestId,
                        "LOAN-AGENT-01",
                        """
                        {
                          "decision": "ALLOW",
                          "systemOutcome": "ERROR",
                          "reasonCodes": ["DOWNSTREAM_TIMEOUT"],
                          "downstreamReached": true,
                          "responseReleased": false,
                          "success": false,
                          "errorLocation": "MOCK_FINANCE",
                          "policyVersion": "loan-review-policy-1",
                          "completedAt": "%s"
                        }
                        """
                                .formatted(COMPLETED_AT));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("decision").asText()).isEqualTo("ALLOW");
        assertThat(response.getBody().get("status").asText()).isEqualTo("ERROR");
        assertThat(response.getBody().get("errorLocation").asText()).isEqualTo("MOCK_FINANCE");
        assertThat(response.getBody().get("reasonCodes").get(0).asText())
                .isEqualTo("DOWNSTREAM_TIMEOUT");
    }

    /**
     * {@code contracts/audit/execution-outcome.schema.json}은 ERROR에 {@code errorLocation}과
     * {@code success=false}를 요구한다. 이걸 받지 않으면 모든 ERROR 기록이 스키마 위반으로 남는다.
     */
    @Test
    void rejectsAnErrorOutcomeWithoutErrorLocation() {
        String requestId = requestId();
        createAudit(requestId, "LOAN-AGENT-01", "LOAN-AGENT-01", true);

        ResponseEntity<JsonNode> response =
                updateOutcome(
                        requestId,
                        "LOAN-AGENT-01",
                        """
                        {
                          "decision": "ALLOW",
                          "systemOutcome": "ERROR",
                          "reasonCodes": ["DOWNSTREAM_TIMEOUT"],
                          "downstreamReached": true,
                          "responseReleased": false,
                          "success": false,
                          "completedAt": "%s"
                        }
                        """
                                .formatted(COMPLETED_AT));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(auditStatus(requestId)).isEqualTo("PROCESSING");
    }

    /** ERROR인데 {@code success=true}는 스키마가 금지한다. */
    @Test
    void rejectsAnErrorOutcomeThatClaimsSuccess() {
        String requestId = requestId();
        createAudit(requestId, "LOAN-AGENT-01", "LOAN-AGENT-01", true);

        ResponseEntity<JsonNode> response =
                updateOutcome(
                        requestId,
                        "LOAN-AGENT-01",
                        """
                        {
                          "decision": "ALLOW",
                          "systemOutcome": "ERROR",
                          "reasonCodes": ["DOWNSTREAM_TIMEOUT"],
                          "downstreamReached": true,
                          "responseReleased": false,
                          "success": true,
                          "errorLocation": "MOCK_FINANCE",
                          "completedAt": "%s"
                        }
                        """
                                .formatted(COMPLETED_AT));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(auditStatus(requestId)).isEqualTo("PROCESSING");
    }

    /** ALLOW + COMPLETED인데 {@code success}가 참이 아니면 스키마가 금지한다. */
    @Test
    void rejectsAnAllowedCompletionThatDidNotSucceed() {
        String requestId = requestId();
        createAudit(requestId, "LOAN-AGENT-01", "LOAN-AGENT-01", true);

        ResponseEntity<JsonNode> response =
                updateOutcome(
                        requestId,
                        "LOAN-AGENT-01",
                        """
                        {
                          "decision": "ALLOW",
                          "systemOutcome": "COMPLETED",
                          "reasonCodes": [],
                          "downstreamReached": true,
                          "responseReleased": true,
                          "success": false,
                          "completedAt": "%s"
                        }
                        """
                                .formatted(COMPLETED_AT));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(auditStatus(requestId)).isEqualTo("PROCESSING");
    }

    @Test
    void rejectsABlockedOutcomeThatClaimsDownstreamWasReached() {
        String requestId = requestId();
        createAudit(requestId, "LOAN-AGENT-01", "LOAN-AGENT-01", true);

        ResponseEntity<JsonNode> response =
                updateOutcome(
                        requestId,
                        "LOAN-AGENT-01",
                        """
                        {
                          "decision": "BLOCK",
                          "systemOutcome": "COMPLETED",
                          "reasonCodes": ["CASE_SCOPE_VIOLATION"],
                          "downstreamReached": true,
                          "responseReleased": false,
                          "completedAt": "%s"
                        }
                        """
                                .formatted(COMPLETED_AT));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(auditStatus(requestId)).isEqualTo("PROCESSING");
    }

    @Test
    void doesNotAllowAFinalAuditToBeOverwritten() {
        String requestId = requestId();
        createAudit(requestId, "LOAN-AGENT-01", "LOAN-AGENT-01", true);
        String outcome =
                """
                {
                  "decision": "BLOCK",
                  "systemOutcome": "COMPLETED",
                  "reasonCodes": ["CASE_SCOPE_VIOLATION"],
                  "downstreamReached": false,
                  "responseReleased": false,
                  "completedAt": "%s"
                }
                """
                        .formatted(COMPLETED_AT);
        ResponseEntity<JsonNode> first = updateOutcome(requestId, "LOAN-AGENT-01", outcome);

        ResponseEntity<JsonNode> duplicate = updateOutcome(requestId, "LOAN-AGENT-01", outcome);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicate.getBody()).isNotNull();
        assertThat(duplicate.getBody().get("reasonCode").asText()).isEqualTo("DUPLICATE_REQUEST");
    }

    @Test
    void reportsMissingAuditWithoutCreatingAReplacement() {
        String requestId = requestId();

        ResponseEntity<JsonNode> response =
                updateOutcome(
                        requestId,
                        "LOAN-AGENT-01",
                        """
                        {
                          "decision": "BLOCK",
                          "systemOutcome": "COMPLETED",
                          "reasonCodes": ["CONTEXT_NOT_FOUND"],
                          "downstreamReached": false,
                          "responseReleased": false,
                          "completedAt": "%s"
                        }
                        """
                                .formatted(COMPLETED_AT));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("reasonCode").asText()).isEqualTo("CONTEXT_NOT_FOUND");
        assertThat(auditCount(requestId)).isZero();
    }

    @Test
    void recordsAuthFailureWithoutCreatingBusinessAudit() {
        String requestId = requestId();

        ResponseEntity<JsonNode> response = recordAuthFailure(requestId, HASH_FINGERPRINT, true);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("securityEventId").asText()).startsWith("SEC-");
        assertThat(body.get("eventType").asText()).isEqualTo("AUTH_FAILURE");
        assertThat(body.get("reasonCode").asText()).isEqualTo("AGENT_AUTHENTICATION_FAILED");
        assertThat(auditCount(requestId)).isZero();
        assertThat(securityEventCount(requestId)).isEqualTo(1);
    }

    @Test
    void allowsRepeatedAuthenticationFailuresForTheSameRequest() {
        String requestId = requestId();

        ResponseEntity<JsonNode> first = recordAuthFailure(requestId, HASH_FINGERPRINT, true);
        ResponseEntity<JsonNode> second = recordAuthFailure(requestId, HASH_FINGERPRINT, true);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(securityEventCount(requestId)).isEqualTo(2);
        assertThat(auditCount(requestId)).isZero();
    }

    @Test
    void rejectsRawSourceMetadataInsteadOfPersistingIt() {
        String requestId = requestId();

        ResponseEntity<JsonNode> response = recordAuthFailure(requestId, "192.0.2.10", true);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(securityEventCount(requestId)).isZero();
        assertThat(auditCount(requestId)).isZero();
    }

    @Test
    void rejectsSecurityEventWithoutInternalCredentialBeforePersistence() {
        String requestId = requestId();

        ResponseEntity<JsonNode> response = recordAuthFailure(requestId, HASH_FINGERPRINT, false);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(securityEventCount(requestId)).isZero();
        assertThat(auditCount(requestId)).isZero();
    }

    private ResponseEntity<JsonNode> createAudit(
            String requestId,
            String bodyAgentId,
            String headerAgentId,
            boolean includeCredential) {
        HttpHeaders headers = internalHeaders(includeCredential);
        if (headerAgentId != null) {
            headers.set(VERIFIED_AGENT_HEADER, headerAgentId);
        }
        String body =
                """
                {
                  "requestId": "%s",
                  "traceId": "trace-21",
                  "agentRunId": "RUN-21",
                  "verifiedAgentId": "%s",
                  "caseId": "LOAN-2026-001",
                  "targetConsumerId": "CUST-1001",
                  "requestedTool": "CREDIT_SCORE_READ",
                  "status": "PROCESSING",
                  "requestedAt": "%s"
                }
                """
                        .formatted(requestId, bodyAgentId, REQUESTED_AT);
        return exchange("/internal/v1/audits", HttpMethod.POST, body, headers);
    }

    private ResponseEntity<JsonNode> updateOutcome(
            String requestId, String verifiedAgentId, String body) {
        HttpHeaders headers = internalHeaders(true);
        headers.set(VERIFIED_AGENT_HEADER, verifiedAgentId);
        return exchange(
                "/internal/v1/audits/" + requestId + "/outcome",
                HttpMethod.PATCH,
                body,
                headers);
    }

    private ResponseEntity<JsonNode> recordAuthFailure(
            String requestId, String sourceFingerprint, boolean includeCredential) {
        String body =
                """
                {
                  "requestId": "%s",
                  "traceId": "trace-auth-21",
                  "eventType": "AUTH_FAILURE",
                  "reasonCode": "AGENT_AUTHENTICATION_FAILED",
                  "credentialType": "AGENT_SERVICE",
                  "sourceFingerprint": "%s",
                  "occurredAt": "%s"
                }
                """
                        .formatted(requestId, sourceFingerprint, REQUESTED_AT);
        return exchange(
                "/internal/v1/security-events/auth-failure",
                HttpMethod.POST,
                body,
                internalHeaders(includeCredential));
    }

    private ResponseEntity<JsonNode> exchange(
            String path, HttpMethod method, String body, HttpHeaders headers) {
        return restTemplate.exchange(
                URI.create(base() + path), method, new HttpEntity<>(body, headers), JsonNode.class);
    }

    private HttpHeaders internalHeaders(boolean includeCredential) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (includeCredential) {
            headers.set(InternalCredentialFilter.CREDENTIAL_HEADER, "test-internal-credential");
        }
        return headers;
    }

    private int auditCount(String requestId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from audit_events where request_id = ?", Integer.class, requestId);
    }

    private int securityEventCount(String requestId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from security_auth_events where request_id = ?",
                Integer.class,
                requestId);
    }

    private String auditStatus(String requestId) {
        return jdbcTemplate.queryForObject(
                "select status from audit_events where request_id = ?", String.class, requestId);
    }

    private String requestId() {
        return "REQ-" + UUID.randomUUID();
    }

    private String base() {
        return "http://localhost:" + port;
    }
}
