package io.finguard.core.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
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
@ActiveProfiles("local")
@Testcontainers
class CoreApiCredentialFilterHttpTest {

    /**
     * Operator Credential이 묶인 EMP-101이 아닌, <strong>실재하는</strong> 다른 직원.
     *
     * <p>없는 직원 ID로 시험하면 조회 실패(422)에 가려 권한 상승이 드러나지 않는다. 대조가 없을 때 이
     * 직원의 도구·데이터 권한이 박힌 Passport가 실제로 발급되는지를 봐야 한다.
     */
    private static final String ANOTHER_EMPLOYEE = "EMP-102";

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

    /** 데모 시드에는 EMP-101 하나뿐이라 대조할 상대를 여기서 만든다. */
    @BeforeEach
    void seedAnotherEmployeeWithRealAuthority() {
        jdbcTemplate.update(
                "insert into employees (employee_id, created_at) values (?, now())"
                        + " on conflict (employee_id) do nothing",
                ANOTHER_EMPLOYEE);
        jdbcTemplate.update(
                "insert into employee_authorities (employee_id, status, allowed_customer_scope, version)"
                        + " values (?, 'ACTIVE', 'ALL', 1) on conflict (employee_id) do nothing",
                ANOTHER_EMPLOYEE);
        for (String tool : List.of("CREDIT_SCORE_READ", "INCOME_READ", "DEBT_READ")) {
            jdbcTemplate.update(
                    "insert into employee_authority_allowed_tools (employee_id, tool) values (?, ?)"
                            + " on conflict (employee_id, tool) do nothing",
                    ANOTHER_EMPLOYEE,
                    tool);
        }
        for (String dataType : List.of("CREDIT_SCORE", "INCOME", "DEBT")) {
            jdbcTemplate.update(
                    "insert into employee_authority_allowed_data (employee_id, data_type) values (?, ?)"
                            + " on conflict (employee_id, data_type) do nothing",
                    ANOTHER_EMPLOYEE,
                    dataType);
        }
    }

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

    /**
     * Viewer는 §15 Dashboard 조회만 할 수 있다. AgentRun 생성은 Operator의 것이다 —
     * {@code docs/04-api-contract.md} §2.
     */
    @Test
    void rejectsAgentRunCreationWithAViewerCredential() {
        long before = passportCount();

        ResponseEntity<JsonNode> response = createAgentRun("test-viewer-credential", "EMP-101");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("reasonCode").asText()).isEqualTo("CORE_API_ROLE_FORBIDDEN");
        assertThat(passportCount()).isEqualTo(before);
    }

    /**
     * 인증만 통과하면 임의의 {@code employeeId}로 Passport를 받아갈 수 있어서는 안 된다. Body의 식별자는
     * 조회 대상 표시일 뿐 인증수단이 아니다 — §3.
     *
     * <p>발급된 Passport는 내부적으로 일관돼서 Runtime Resolver가 되돌릴 수 없다. 막을 곳은 여기뿐이다.
     */
    @Test
    void rejectsAgentRunCreationForAnEmployeeOtherThanTheCredentialsOwner() {
        long before = passportCount();

        ResponseEntity<JsonNode> response =
                createAgentRun("test-operator-credential", ANOTHER_EMPLOYEE);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("reasonCode").asText()).isEqualTo("EMPLOYEE_IDENTITY_MISMATCH");
        assertThat(passportCount()).isEqualTo(before);
    }

    private ResponseEntity<JsonNode> createAgentRun(String bearer) {
        return createAgentRun(bearer, "EMP-101");
    }

    private ResponseEntity<JsonNode> createAgentRun(String bearer, String employeeId) {
        HttpHeaders headers = bearerHeaders(bearer);
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body =
                """
                {
                  "employeeId": "%s",
                  "consumerId": "CUST-1001",
                  "taskType": "LOAN_REVIEW",
                  "inputText": "대출 심사를 시작해 주세요"
                }
                """
                        .formatted(employeeId);
        return restTemplate.exchange(
                URI.create(base() + "/api/v1/agent-runs"),
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                JsonNode.class);
    }

    private HttpHeaders bearerHeaders(String bearer) {
        HttpHeaders headers = new HttpHeaders();
        if (bearer != null) {
            headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + bearer);
        }
        return headers;
    }

    private long agentRunCount() {
        return jdbcTemplate.queryForObject("select count(*) from agent_runs", Long.class);
    }

    /** 권한 상승의 실제 산출물은 Passport다. AgentRun만 세면 Passport가 남는 경로를 놓친다. */
    private long passportCount() {
        return jdbcTemplate.queryForObject("select count(*) from task_passports", Long.class);
    }

    private String base() {
        return "http://localhost:" + port;
    }
}
