package io.finguard.core.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.JsonNode;

import io.finguard.core.agentrun.AgentRunLauncher;
import io.finguard.core.security.InternalCredentialFilter;

/**
 * {@code docs/04-api-contract.md} §3.1의 Scenario 일곱 개가 약속한 판정을 실제로 내는지 고정한다.
 *
 * <p>Scenario 이름은 Gateway Body에 실리지 않는다(§3.1). 그래서 "공격"과 "정상"을 가르는 것은
 * 이름이 아니라 <strong>서버 쪽 Fixture</strong>다. Fixture가 넓으면 이름이 공격이어도 ALLOW가
 * 나고, 그러면 데모가 아무것도 증명하지 못한다 — 이슈 #94.
 *
 * <p>Core는 Agent 모듈의 enum을 클래스패스에 두지 않으므로 매핑 표를 여기 다시 적는다. 저장소에
 * 공유 기계가독 정의가 없고, Agent 쪽은 {@code AgentScenarioMappingTest}가 따로 고정한다.
 * 두 표의 단일 출처는 {@code docs/04} §3.1 문서다.
 *
 * <p><strong>두 표는 기계적으로 연결돼 있지 않다.</strong> 이 테스트는 아래 표의 고객으로 직접
 * 실행을 시작하므로, Agent의 enum이 다른 고객을 가리켜도 여기서는 초록으로 남는다. 그쪽은
 * {@code backend/agent/src/test/java/io/finguard/agent/AgentScenarioMappingTest.java}가 잡는다.
 * <strong>셋 중 하나를 고치면 나머지 둘을 함께 고쳐야 한다</strong> — {@code docs/04} §3.1,
 * 이 표, {@code AgentScenarioMappingTest}의 {@code @CsvSource}.
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
class AttackScenarioFixtureTest {

    private static final String REQUEST_ID = "550e8400-e29b-41d4-a716-446655440001";

    /** {@code docs/04} §7 — 성공한 Context 조회가 항상 채워 반환하는 아홉 개. */
    private static final List<String> SCOPE_FIELDS =
            List.of(
                    "employeeAuthority",
                    "permissionTemplate",
                    "caseStatus",
                    "mandate",
                    "passportStatus",
                    "agentBinding",
                    "customerScope",
                    "toolScope",
                    "dataScope");

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
     * Agent 실행 지시를 태우지 않는다. Launcher가 돌면 AgentRun이 COMPLETED나 FAILED가 되고,
     * ContextResolveService는 RUNNING일 때만 관계 검증을 통과시킨다 — 게다가 비동기라 경합이다.
     */
    @MockitoBean
    private AgentRunLauncher agentRunLauncher;

    @BeforeEach
    void clearAuditEvents() {
        jdbcTemplate.update("delete from audit_event_requested_data");
        jdbcTemplate.update("delete from audit_event_reason_codes");
        jdbcTemplate.update("delete from audit_events");
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        // 열 순서: scenario | 실행 consumerId | 조회 targetConsumerId | tool | requestedData | 기대 VIOLATION
        // 실행과 조회 고객은 CASE_SCOPE_ATTACK 에서만 다르다. 나머지가 다르면 customerScope 가
        // 먼저 터져 의도한 위반을 가린다.
        "NORMAL_CREDIT_SCORE, CUST-1001, CUST-1001, CREDIT_SCORE_READ, CREDIT_SCORE, NONE",
        "NORMAL_INCOME, CUST-1001, CUST-1001, INCOME_READ, INCOME, NONE",
        "NORMAL_DEBT, CUST-1001, CUST-1001, DEBT_READ, DEBT, NONE",
        "CASE_SCOPE_ATTACK, CUST-1001, CUST-9999, CREDIT_SCORE_READ, CREDIT_SCORE, customerScope",
        "TOOL_SCOPE_ATTACK, CUST-1002, CUST-1002, INCOME_READ, INCOME, toolScope|dataScope|mandate",
        "DATA_SCOPE_ATTACK, CUST-1002, CUST-1002, CREDIT_SCORE_READ, CREDIT_SCORE|INCOME, dataScope|mandate",
        "MANDATE_SCOPE_ATTACK, CUST-1003, CUST-1003, DEBT_READ, DEBT, toolScope|dataScope|mandate",
    })
    void producesTheScopeStatusThatTheContractPromises(
            String scenario,
            String runConsumerId,
            String targetConsumerId,
            String tool,
            String requestedData,
            String expectedViolations) {
        List<String> data = Arrays.asList(requestedData.split("\\|"));
        Set<String> violations =
                "NONE".equals(expectedViolations)
                        ? Set.of()
                        : Arrays.stream(expectedViolations.split("\\|")).collect(Collectors.toSet());

        RunReferences run = startAgentRun(scenario, runConsumerId);
        createAudit(run, targetConsumerId, tool);

        ResponseEntity<JsonNode> response = resolve(run, targetConsumerId, tool, data);

        assertThat(response.getStatusCode())
                .as("%s 는 해석에 성공해야 한다", scenario)
                .isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        JsonNode scope = response.getBody().get("scopeStatus");
        assertThat(scope.size()).isEqualTo(9);
        // 지목한 위반만 보면 의도치 않은 추가 위반이 조용히 통과한다. 아홉 개를 전부 본다.
        for (String field : SCOPE_FIELDS) {
            String expected = violations.contains(field) ? "VIOLATION" : "OK";
            assertThat(scope.get(field).asText()).as("%s → %s", scenario, field).isEqualTo(expected);
        }
    }

    /**
     * {@code scenario}를 실제로 실려 보낸다. 판정에는 쓰이지 않지만 Core가 그 이름을 아는지가
     * 여기서 걸러진다 — 모르는 값이면 역직렬화에서 400이 난다. 이슈 #89가 정확히 그 상태였다.
     */
    private RunReferences startAgentRun(String scenario, String consumerId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer test-operator-credential");
        ResponseEntity<JsonNode> response =
                restTemplate.exchange(
                        URI.create(base() + "/api/v1/agent-runs"),
                        HttpMethod.POST,
                        new HttpEntity<>(
                                """
                                {
                                  "employeeId": "EMP-101",
                                  "consumerId": "%s",
                                  "taskType": "LOAN_REVIEW",
                                  "inputText": "%s의 대출심사를 진행해줘.",
                                  "scenario": "%s"
                                }
                                """
                                        .formatted(consumerId, consumerId, scenario),
                                headers),
                        JsonNode.class);

        assertThat(response.getStatusCode())
                .as("%s 로 실행을 시작할 수 있어야 한다 — Mandate 시드가 없으면 여기서 죽는다", consumerId)
                .isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        JsonNode body = response.getBody();
        return new RunReferences(
                body.get("agentRunId").asText(),
                body.get("passportId").asText(),
                body.get("caseId").asText(),
                body.get("agentId").asText());
    }

    /** 선저장 Business Audit. 없으면 resolve가 409로 거부한다. */
    private void createAudit(RunReferences run, String targetConsumerId, String tool) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(InternalCredentialFilter.CREDENTIAL_HEADER, "test-internal-credential");
        headers.set("X-Verified-Agent-Id", run.agentId());
        ResponseEntity<JsonNode> response =
                restTemplate.exchange(
                        URI.create(base() + "/internal/v1/audits"),
                        HttpMethod.POST,
                        new HttpEntity<>(
                                """
                                {
                                  "requestId": "%s",
                                  "traceId": "4bf92f0000000001",
                                  "agentRunId": "%s",
                                  "verifiedAgentId": "%s",
                                  "caseId": "%s",
                                  "targetConsumerId": "%s",
                                  "requestedTool": "%s",
                                  "status": "PROCESSING",
                                  "requestedAt": "2026-08-25T12:00:00Z"
                                }
                                """
                                        .formatted(
                                                REQUEST_ID,
                                                run.agentRunId(),
                                                run.agentId(),
                                                run.caseId(),
                                                targetConsumerId,
                                                tool),
                                headers),
                        JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private ResponseEntity<JsonNode> resolve(
            RunReferences run, String targetConsumerId, String tool, List<String> requestedData) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(InternalCredentialFilter.CREDENTIAL_HEADER, "test-internal-credential");
        // agentBinding을 OK로 만들려면 Passport가 들고 있는 Agent여야 한다. 응답의 agentId를 그대로 쓴다.
        headers.set("X-Verified-Agent-Id", run.agentId());
        String dataArray =
                requestedData.stream().map(d -> "\"" + d + "\"").collect(Collectors.joining(", "));
        String body =
                """
                {
                  "requestId": "%s",
                  "verifiedAgentId": "%s",
                  "agentRunId": "%s",
                  "passportId": "%s",
                  "targetConsumerId": "%s",
                  "requestedTool": "%s",
                  "requestedData": [%s]
                }
                """
                        .formatted(
                                REQUEST_ID,
                                run.agentId(),
                                run.agentRunId(),
                                run.passportId(),
                                targetConsumerId,
                                tool,
                                dataArray);
        return restTemplate.exchange(
                URI.create(base() + "/internal/v1/context/resolve"),
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                JsonNode.class);
    }

    private String base() {
        return "http://localhost:" + port;
    }

    private record RunReferences(String agentRunId, String passportId, String caseId, String agentId) {
    }
}
