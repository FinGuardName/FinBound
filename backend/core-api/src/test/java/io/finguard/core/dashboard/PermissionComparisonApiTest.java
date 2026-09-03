package io.finguard.core.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;

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

import io.finguard.core.domain.AgentRun;
import io.finguard.core.domain.AgentRunStatus;
import io.finguard.core.domain.CustomerScope;
import io.finguard.core.domain.DataType;
import io.finguard.core.domain.EmployeeAuthority;
import io.finguard.core.domain.EmployeeAuthorityStatus;
import io.finguard.core.domain.SourceVersions;
import io.finguard.core.domain.TaskPassport;
import io.finguard.core.domain.TaskPassportStatus;
import io.finguard.core.domain.TaskType;
import io.finguard.core.domain.Tool;
import io.finguard.core.repository.AgentRunRepository;
import io.finguard.core.repository.EmployeeAuthorityRepository;
import io.finguard.core.repository.TaskPassportRepository;

/**
 * {@code GET /api/v1/agent-runs/{agentRunId}/permission-comparison} — docs/04 §15.
 *
 * <p>화면이 답해야 하는 질문은 하나다: <strong>이 직원이 원래 할 수 있는 일 중 이번 업무에서
 * Agent에게 넘어간 것은 어디까지인가.</strong> 넘어가지 않은 부분이 보안의 값어치다.
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
class PermissionComparisonApiTest {

    private static final Instant ISSUED = Instant.parse("2026-08-25T10:00:00Z");
    private static final Instant EXPIRES = Instant.parse("2030-12-31T14:59:59Z");

    @Container
    @ServiceConnection
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.10-alpine");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EmployeeAuthorityRepository authorities;

    @Autowired
    private TaskPassportRepository passports;

    @Autowired
    private AgentRunRepository agentRuns;

    @BeforeEach
    void seed() {
        agentRuns.deleteAll();
        passports.deleteAll();
        authorities.deleteAll();
        jdbcTemplate.update("delete from financial_cases");
        jdbcTemplate.update("delete from consumers where consumer_id = 'CUST-700'");
        jdbcTemplate.update("delete from employees where employee_id = 'EMP-700'");

        jdbcTemplate.update(
                "insert into employees (employee_id, created_at) values ('EMP-700', ?)",
                java.sql.Timestamp.from(ISSUED));
        jdbcTemplate.update(
                "insert into consumers (consumer_id, created_at) values ('CUST-700', ?)",
                java.sql.Timestamp.from(ISSUED));
        jdbcTemplate.update(
                """
                insert into permission_templates (
                    template_id, task_type, default_duration_minutes, status, version)
                values ('TPL-700', 'LOAN_REVIEW', 60, 'ACTIVE', 0)
                on conflict do nothing
                """);
        jdbcTemplate.update(
                """
                insert into financial_cases (
                    case_id, employee_id, consumer_id, task_type, template_id,
                    status, issued_at, expires_at, version)
                values ('LOAN-2026-700', 'EMP-700', 'CUST-700', 'LOAN_REVIEW', 'TPL-700',
                    'ACTIVE', ?, ?, 0)
                """,
                java.sql.Timestamp.from(ISSUED),
                java.sql.Timestamp.from(EXPIRES));

        // 직원은 도구 셋 전부를 쓸 수 있다.
        authorities.save(
                new EmployeeAuthority(
                        "EMP-700",
                        EmployeeAuthorityStatus.ACTIVE,
                        CustomerScope.ALL,
                        EnumSet.allOf(Tool.class),
                        EnumSet.allOf(DataType.class)));

        // 이번 업무의 Passport는 그중 하나만 넘겨받았다.
        passports.save(
                new TaskPassport(
                        "PASS-700",
                        "LOAN-AGENT-01",
                        "EMP-700",
                        "LOAN-2026-700",
                        "CUST-700",
                        TaskType.LOAN_REVIEW,
                        EnumSet.of(Tool.CREDIT_SCORE_READ),
                        EnumSet.of(DataType.CREDIT_SCORE),
                        TaskPassportStatus.ACTIVE,
                        ISSUED,
                        EXPIRES,
                        new SourceVersions(1L, 1L, 1L, 1L)));

        agentRuns.save(
                new AgentRun(
                        "RUN-700",
                        "LOAN-AGENT-01",
                        "EMP-700",
                        "LOAN-2026-700",
                        "PASS-700",
                        List.of("INPUT-700"),
                        AgentRunStatus.CREATED,
                        ISSUED));
    }

    @Test
    void comparisonShowsWhatTheEmployeeCanDoAndWhatTheAgentActuallyGot() {
        JsonNode body = getAsViewer("/api/v1/agent-runs/RUN-700/permission-comparison").getBody();

        assertThat(body).isNotNull();
        assertThat(body.get("agentRunId").asText()).isEqualTo("RUN-700");
        assertThat(body.get("employeeId").asText()).isEqualTo("EMP-700");
        assertThat(body.get("passportId").asText()).isEqualTo("PASS-700");

        assertThat(toList(body.get("employeeAuthority").get("allowedTools")))
                .containsExactlyInAnyOrder("CREDIT_SCORE_READ", "INCOME_READ", "DEBT_READ");
        assertThat(toList(body.get("agentEffectivePermission").get("allowedTools")))
                .containsExactly("CREDIT_SCORE_READ");

        // 이 목록이 이 화면의 존재 이유다 — 직원은 되는데 Agent는 안 되는 것.
        assertThat(toList(body.get("withheldTools")))
                .containsExactlyInAnyOrder("INCOME_READ", "DEBT_READ");
        assertThat(toList(body.get("withheldData")))
                .containsExactlyInAnyOrder("INCOME", "DEBT");
    }

    @Test
    void comparisonIsClosedToCallersWithoutACredential() {
        ResponseEntity<JsonNode> response =
                restTemplate.exchange(
                        "/api/v1/agent-runs/RUN-700/permission-comparison",
                        HttpMethod.GET,
                        new HttpEntity<>(new HttpHeaders()),
                        JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private List<String> toList(JsonNode array) {
        return java.util.stream.StreamSupport.stream(array.spliterator(), false)
                .map(JsonNode::asText)
                .toList();
    }

    private ResponseEntity<JsonNode> getAsViewer(String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("test-viewer-credential");
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
    }
}
