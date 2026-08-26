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

    /**
     * 401은 어떤 인증을 기대하는지 밝혀야 한다 — RFC 9110 §11.6.1이 {@code WWW-Authenticate}를
     * 필수로 요구한다.
     *
     * <p>{@code Bearer}는 브라우저 기본 인증 대화상자를 띄우지 않는다. {@code Basic}이었다면 팝업이
     * 떠서 Vue 화면 위에 브라우저 UI가 겹쳤을 것이다.
     */
    @Test
    void tellsTheClientWhichAuthenticationSchemeItExpects() {
        ResponseEntity<JsonNode> response = createAgentRun(null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
    }

    /**
     * 거부된 요청도 흔적을 남긴다. {@code docs/04-api-contract.md} §2가 {@code CORE_API_BEARER}인 최소
     * {@code SecurityAuthEvent}를 요구한다.
     *
     * <p>기록이 없으면 자격 증명을 반복해서 찔러보는 행위가 관측되지 않는다 — 막기는 하되 몇 번
     * 두드렸는지 아무도 모르는 상태가 된다.
     */
    @Test
    void recordsASecurityEventWhenTheCredentialIsRejected() {
        long before = securityEventCount("CORE_API_CREDENTIAL_INVALID");

        createAgentRun(null);

        assertThat(securityEventCount("CORE_API_CREDENTIAL_INVALID")).isEqualTo(before + 1);
        assertThat(latestSecurityEventColumn("CORE_API_CREDENTIAL_INVALID", "credential_type"))
                .isEqualTo("CORE_API_BEARER");
        assertThat(latestSecurityEventColumn("CORE_API_CREDENTIAL_INVALID", "event_type"))
                .isEqualTo("AUTH_FAILURE");
    }

    @Test
    void recordsASecurityEventWhenTheRoleIsNotAllowed() {
        long before = securityEventCount("CORE_API_ROLE_FORBIDDEN");

        createAgentRun("test-viewer-credential", "EMP-101");

        assertThat(securityEventCount("CORE_API_ROLE_FORBIDDEN")).isEqualTo(before + 1);
    }

    @Test
    void recordsASecurityEventWhenTheEmployeeDoesNotMatch() {
        long before = securityEventCount("EMPLOYEE_IDENTITY_MISMATCH");

        createAgentRun("test-operator-credential", ANOTHER_EMPLOYEE);

        assertThat(securityEventCount("EMPLOYEE_IDENTITY_MISMATCH")).isEqualTo(before + 1);
    }

    /** 인증에 실패한 요청은 업무 감사 기록을 만들지 않는다 — {@code docs/06-common-conventions.md} §10. */
    @Test
    void neverCreatesABusinessAuditForARejectedRequest() {
        long before = auditEventCount();

        createAgentRun(null);
        createAgentRun("test-viewer-credential", "EMP-101");
        createAgentRun("test-operator-credential", ANOTHER_EMPLOYEE);

        assertThat(auditEventCount()).isEqualTo(before);
    }

    /** Credential 원문은 어디에도 남지 않는다 — {@code docs/06} §26. */
    @Test
    void neverStoresThePresentedCredential() {
        createAgentRun("wrong-credential-that-must-not-be-stored");

        Long matches =
                jdbcTemplate.queryForObject(
                        "select count(*) from security_auth_events"
                                + " where request_id like ? or trace_id like ? or source_fingerprint like ?",
                        Long.class,
                        "%wrong-credential%",
                        "%wrong-credential%",
                        "%wrong-credential%");
        assertThat(matches).isZero();
    }

    /**
     * CORS preflight는 Credential을 싣지 않으므로 401을 받는다. <strong>이건 고장이 아니라 결론이다.</strong>
     *
     * <p>브라우저에서 Core를 직접 부르려면 preflight를 인증 없이 통과시켜야 하는데, 그건
     * {@code docs/04-api-contract.md} §2의 "{@code /api/v1/**}에는 인증 없는 기본 경로를 두지 않는다"와
     * 정면으로 어긋난다. 그래서 Core에 CORS를 열지 않고 Vite에 same-origin proxy를 두기로 했다 —
     * 그 편이 Credential을 브라우저에서 더 멀리 두기도 한다.
     *
     * <p>이 테스트는 누군가 나중에 CORS를 켜려 할 때 그 결정을 다시 마주하게 하려고 있다.
     */
    @Test
    void refusesAPreflightBecauseItCarriesNoCredential() {
        HttpHeaders preflight = new HttpHeaders();
        preflight.set(HttpHeaders.ORIGIN, "http://localhost:5173");
        preflight.set(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST");
        preflight.set(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type");

        ResponseEntity<String> response =
                restTemplate.exchange(
                        URI.create(base() + "/api/v1/agent-runs"),
                        HttpMethod.OPTIONS,
                        new HttpEntity<>(preflight),
                        String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getAccessControlAllowOrigin()).isNull();
    }

    /**
     * {@code Authorization} 헤더가 둘이면 거부한다.
     *
     * <p>어느 쪽을 유효한 값으로 고를지는 컨테이너·프록시마다 다르다. 앞의 것을 보는 중간 장비와 뒤의
     * 것을 보는 Core가 섞이면, 유효한 헤더 하나와 원하는 헤더 하나를 함께 보내는 것으로 판정을 갈라놓을
     * 수 있다. 고르지 않고 거부한다.
     */
    @Test
    void rejectsARequestCarryingTwoAuthorizationHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(HttpHeaders.AUTHORIZATION, "Bearer test-operator-credential");
        headers.add(HttpHeaders.AUTHORIZATION, "Bearer test-viewer-credential");

        assertThat(postAgentRun(headers).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /** scheme 없이 값만 보내는 것을 받아주면 {@code Bearer} 계약이 사실상 없는 것이 된다. */
    @Test
    void rejectsACredentialSentWithoutTheBearerScheme() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.AUTHORIZATION, "test-operator-credential");

        assertThat(postAgentRun(headers).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /**
     * 소문자 {@code bearer}도 거부한다.
     *
     * <p>RFC 상 scheme은 대소문자를 가리지 않지만, 이 계약은 받아들이는 형태를 하나로 좁히기로 했다.
     * 넓게 받으면 그만큼 필터와 다른 장비의 해석이 갈릴 여지가 생긴다.
     */
    @Test
    void rejectsALowercaseBearerScheme() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.AUTHORIZATION, "bearer test-operator-credential");

        assertThat(postAgentRun(headers).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rejectsABearerSchemeWithNoTokenAfterIt() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer ");

        assertThat(postAgentRun(headers).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private ResponseEntity<JsonNode> postAgentRun(HttpHeaders headers) {
        return restTemplate.exchange(
                URI.create(base() + "/api/v1/agent-runs"),
                HttpMethod.POST,
                new HttpEntity<>(
                        """
                        {
                          "employeeId": "EMP-101",
                          "consumerId": "CUST-1001",
                          "taskType": "LOAN_REVIEW",
                          "inputText": "대출 심사를 시작해 주세요"
                        }
                        """,
                        headers),
                JsonNode.class);
    }

    /** actuator는 필터 등록 패턴 밖이다. 인증 경계가 어디까지인지 명시해 둔다. */
    @Test
    void leavesActuatorOutsideTheCredentialFilter() {
        ResponseEntity<String> response =
                restTemplate.exchange(
                        URI.create(base() + "/actuator/health"),
                        HttpMethod.GET,
                        new HttpEntity<>(new HttpHeaders()),
                        String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
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

    private long securityEventCount(String reasonCode) {
        return jdbcTemplate.queryForObject(
                "select count(*) from security_auth_events where reason_code = ?",
                Long.class,
                reasonCode);
    }

    private String latestSecurityEventColumn(String reasonCode, String column) {
        return jdbcTemplate.queryForObject(
                "select " + column + " from security_auth_events where reason_code = ?"
                        + " order by occurred_at desc limit 1",
                String.class,
                reasonCode);
    }

    private long auditEventCount() {
        return jdbcTemplate.queryForObject("select count(*) from audit_events", Long.class);
    }

    private String base() {
        return "http://localhost:" + port;
    }
}
