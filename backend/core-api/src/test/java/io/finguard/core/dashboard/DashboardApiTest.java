package io.finguard.core.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
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

/**
 * Dashboard 조회 API의 실제 HTTP/PostgreSQL 경계. {@code docs/04-api-contract.md} §15.
 *
 * <p>Vue는 PostgreSQL을 직접 조회하지 않으므로 이 API가 유일한 통로다.
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
class DashboardApiTest {

    @Container
    @ServiceConnection
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.10-alpine");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetAuditEvents() {
        jdbcTemplate.update("delete from audit_event_requested_data");
        jdbcTemplate.update("delete from audit_event_reason_codes");
        jdbcTemplate.update("delete from audit_events");
    }

    @Test
    void summaryCountsAllowBlockAndErrorSeparately() {
        insertAudit("AUD-001", "REQ-001", "ALLOW", "COMPLETED", "2026-08-25T10:00:00Z");
        insertAudit("AUD-002", "REQ-002", "BLOCK", "COMPLETED", "2026-08-25T10:01:00Z");
        insertAudit("AUD-003", "REQ-003", "BLOCK", "COMPLETED", "2026-08-25T10:02:00Z");
        // ERROR는 판정이 ALLOW여도 결과가 ERROR다 — 둘은 다른 축이다(docs/06 §12).
        insertAudit("AUD-004", "REQ-004", "ALLOW", "ERROR", "2026-08-25T10:03:00Z");

        JsonNode body = getAsViewer("/api/v1/dashboard/summary").getBody();

        assertThat(body).isNotNull();
        assertThat(body.get("total").asInt()).isEqualTo(4);
        assertThat(body.get("allow").asInt()).isEqualTo(1);
        assertThat(body.get("block").asInt()).isEqualTo(2);
        assertThat(body.get("error").asInt()).isEqualTo(1);
    }

    @Test
    void auditEventListIsNewestFirstAndPaginated() {
        insertAudit("AUD-001", "REQ-001", "ALLOW", "COMPLETED", "2026-08-25T10:00:00Z");
        insertAudit("AUD-002", "REQ-002", "BLOCK", "COMPLETED", "2026-08-25T10:01:00Z");
        insertAudit("AUD-003", "REQ-003", "BLOCK", "COMPLETED", "2026-08-25T10:02:00Z");

        JsonNode body = getAsViewer("/api/v1/audit-events?page=1&pageSize=2").getBody();

        assertThat(body).isNotNull();
        assertThat(body.get("totalItems").asInt()).isEqualTo(3);
        assertThat(body.get("totalPages").asInt()).isEqualTo(2);
        assertThat(body.get("items")).hasSize(2);
        // docs/06 §25 — 기본 정렬은 requestedAt DESC다.
        assertThat(body.get("items").get(0).get("auditEventId").asText()).isEqualTo("AUD-003");
        assertThat(body.get("items").get(1).get("auditEventId").asText()).isEqualTo("AUD-002");
    }

    @Test
    void auditEventListFiltersByStoredColumns() {
        insertAudit("AUD-001", "REQ-001", "ALLOW", "COMPLETED", "2026-08-25T10:00:00Z");
        insertAudit("AUD-002", "REQ-002", "BLOCK", "COMPLETED", "2026-08-25T10:01:00Z");

        JsonNode body = getAsViewer("/api/v1/audit-events?outcome=BLOCK").getBody();

        assertThat(body).isNotNull();
        assertThat(body.get("totalItems").asInt()).isEqualTo(1);
        assertThat(body.get("items").get(0).get("auditEventId").asText()).isEqualTo("AUD-002");
    }

    @Test
    void auditEventDetailExposesTheContractFields() {
        insertAudit("AUD-001", "REQ-001", "ALLOW", "COMPLETED", "2026-08-25T10:00:00Z");

        JsonNode body = getAsViewer("/api/v1/audit-events/AUD-001").getBody();

        assertThat(body).isNotNull();
        assertThat(body.get("auditEventId").asText()).isEqualTo("AUD-001");
        assertThat(body.get("decision").asText()).isEqualTo("ALLOW");
        assertThat(body.get("systemOutcome").asText()).isEqualTo("COMPLETED");
        // 아직 Resolver를 거치지 않은 기록이라 근거는 비어 있다. 칸이 없는 것과 다르다.
        assertThat(body.hasNonNull("scopeStatus")).isFalse();
    }

    @Test
    void processingEventReportsNoSystemOutcome() {
        // audit-event.schema.json은 systemOutcome을 COMPLETED|ERROR로만 정의한다.
        // status는 PROCESSING을 허용한다 — 둘은 다른 속성이고 진행 중인 요청에는 결과가 없다.
        insertProcessingAudit("AUD-100", "REQ-100", "2026-08-25T10:00:00Z");

        JsonNode body = getAsViewer("/api/v1/audit-events/AUD-100").getBody();

        assertThat(body).isNotNull();
        assertThat(body.get("status").asText()).isEqualTo("PROCESSING");
        assertThat(body.hasNonNull("systemOutcome")).isFalse();
    }

    @Test
    void unsupportedPeriodIsARequestErrorNotAServerError() {
        // 서버가 고장난 게 아니라 호출자가 없는 값을 보낸 것이다.
        ResponseEntity<JsonNode> response = getAsViewer("/api/v1/audit-events?period=7D");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void unknownAuditEventIsNotFoundRatherThanAServerError() {
        // 인증된 호출자가 없는 자원을 물었을 뿐이다. 500이면 프론트가 장애로 읽고,
        // 403이면 "있는데 못 본다"로 읽혀 존재 여부가 샌다.
        ResponseEntity<JsonNode> response = getAsViewer("/api/v1/audit-events/AUD-DOES-NOT-EXIST");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void dashboardIsClosedToCallersWithoutACredential() {
        ResponseEntity<JsonNode> response =
                restTemplate.exchange(
                        "/api/v1/dashboard/summary",
                        HttpMethod.GET,
                        new HttpEntity<>(new HttpHeaders()),
                        JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private ResponseEntity<JsonNode> getAsViewer(String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("test-viewer-credential");
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
    }

    private void insertProcessingAudit(String auditEventId, String requestId, String requestedAt) {
        jdbcTemplate.update(
                """
                insert into audit_events (
                    audit_event_id, request_id, trace_id, agent_id, agent_run_id,
                    case_id, target_consumer_id, requested_tool, status, requested_at, version)
                values (?, ?, 'trace-1', 'LOAN-AGENT-01', 'RUN-001',
                    'LOAN-2026-001', 'CUST-1001', 'CREDIT_SCORE_READ', 'PROCESSING', ?, 0)
                """,
                auditEventId,
                requestId,
                java.sql.Timestamp.from(Instant.parse(requestedAt)));
    }

    private void insertAudit(
            String auditEventId,
            String requestId,
            String decision,
            String status,
            String requestedAt) {
        jdbcTemplate.update(
                """
                insert into audit_events (
                    audit_event_id, request_id, trace_id, agent_id, agent_run_id,
                    case_id, target_consumer_id, requested_tool, decision, status,
                    requested_at, version)
                values (?, ?, 'trace-1', 'LOAN-AGENT-01', 'RUN-001',
                    'LOAN-2026-001', 'CUST-1001', 'CREDIT_SCORE_READ', ?, ?, ?, 0)
                """,
                auditEventId,
                requestId,
                decision,
                status,
                java.sql.Timestamp.from(Instant.parse(requestedAt)));
    }
}
