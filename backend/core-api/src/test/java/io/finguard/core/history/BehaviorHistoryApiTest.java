package io.finguard.core.history;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.sql.Timestamp;
import java.time.Instant;
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
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.JsonNode;

import io.finguard.core.security.InternalCredentialFilter;

/** Behavior History의 실제 HTTP/PostgreSQL 조회 경계. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "finguard.internal.credential=test-internal-credential")
@Testcontainers
class BehaviorHistoryApiTest {

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
    void returnsRecentCompletedAllowAndBlockEventsOnly() {
        String agentId = "AGENT-" + UUID.randomUUID();
        Instant now = Instant.now();
        String newestAllow =
                insertAudit(agentId, "COMPLETED", "ALLOW", now.minusSeconds(10), true, 120L);
        String olderBlock =
                insertAudit(agentId, "COMPLETED", "BLOCK", now.minusSeconds(20), false, 30L);
        insertAudit(agentId, "PROCESSING", null, now.minusSeconds(5), null, null);
        insertAudit(agentId, "ERROR", null, now.minusSeconds(7), false, 50L);
        insertAudit(agentId, "COMPLETED", "ALLOW", now.minusSeconds(301), true, 80L);
        insertAudit(
                "OTHER-" + UUID.randomUUID(),
                "COMPLETED",
                "ALLOW",
                now.minusSeconds(3),
                true,
                10L);

        ResponseEntity<JsonNode> response = getHistory(agentId, "5m", true);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("agentId").asText()).isEqualTo(agentId);
        assertThat(body.get("window").asText()).isEqualTo("5m");
        assertThat(body.has("hardRequestLimitExceeded")).isFalse();

        JsonNode events = body.get("completedEvents");
        assertThat(events).hasSize(2);
        assertThat(events.get(0).get("requestId").asText()).isEqualTo(newestAllow);
        assertThat(events.get(0).get("caseId").asText()).isEqualTo("LOAN-2026-001");
        assertThat(events.get(0).get("targetConsumerId").asText()).isEqualTo("CUST-1001");
        assertThat(events.get(0).get("tool").asText()).isEqualTo("CREDIT_SCORE_READ");
        assertThat(events.get(0).get("decision").asText()).isEqualTo("ALLOW");
        assertThat(events.get(0).get("success").asBoolean()).isTrue();
        assertThat(events.get(0).get("latencyMs").asLong()).isEqualTo(120L);
        assertThat(events.get(1).get("requestId").asText()).isEqualTo(olderBlock);
        assertThat(events.get(1).get("decision").asText()).isEqualTo("BLOCK");
    }

    @Test
    void returnsAnEmptyCompletedEventListWhenNoHistoryExists() {
        String agentId = "EMPTY-" + UUID.randomUUID();

        ResponseEntity<JsonNode> response = getHistory(agentId, null, true);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("window").asText()).isEqualTo("5m");
        assertThat(response.getBody().get("completedEvents")).isEmpty();
    }

    @Test
    void rejectsInvalidWindowWithoutReturningHistory() {
        ResponseEntity<JsonNode> response =
                getHistory("AGENT-" + UUID.randomUUID(), "five-minutes", true);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("reasonCode").asText())
                .isEqualTo("INVALID_TOOL_REQUEST");
        assertThat(response.getBody().has("completedEvents")).isFalse();
    }

    @Test
    void rejectsHistoryLookupWithoutInternalCredential() {
        ResponseEntity<JsonNode> response =
                getHistory("AGENT-" + UUID.randomUUID(), "5m", false);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("reasonCode").asText())
                .isEqualTo("INTERNAL_CREDENTIAL_INVALID");
    }

    private String insertAudit(
            String agentId,
            String status,
            String decision,
            Instant requestedAt,
            Boolean success,
            Long latencyMs) {
        String suffix = UUID.randomUUID().toString();
        String requestId = "REQ-HISTORY-" + suffix;
        Instant completedAt = "PROCESSING".equals(status) ? null : requestedAt.plusSeconds(1);
        jdbcTemplate.update(
                """
                insert into audit_events (
                    audit_event_id, request_id, trace_id, agent_id, agent_run_id,
                    case_id, target_consumer_id, requested_tool, decision,
                    downstream_reached, response_released, success, records_read,
                    latency_ms, policy_version, status, requested_at, completed_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                "AUD-HISTORY-" + suffix,
                requestId,
                "TRACE-HISTORY-" + suffix,
                agentId,
                "RUN-HISTORY-" + suffix,
                "LOAN-2026-001",
                "CUST-1001",
                "CREDIT_SCORE_READ",
                decision,
                decision == null ? null : !"BLOCK".equals(decision),
                decision == null ? null : !"BLOCK".equals(decision),
                success,
                success == null ? null : 1,
                latencyMs,
                "loan-review-policy-1",
                status,
                Timestamp.from(requestedAt),
                completedAt == null ? null : Timestamp.from(completedAt));
        return requestId;
    }

    private ResponseEntity<JsonNode> getHistory(
            String agentId, String window, boolean includeCredential) {
        HttpHeaders headers = new HttpHeaders();
        if (includeCredential) {
            headers.add(InternalCredentialFilter.CREDENTIAL_HEADER, "test-internal-credential");
        }
        String query = window == null ? "" : "?window=" + window;
        return restTemplate.exchange(
                URI.create(base() + "/internal/v1/agents/" + agentId + "/behavior-history" + query),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                JsonNode.class);
    }

    private String base() {
        return "http://localhost:" + port;
    }
}
