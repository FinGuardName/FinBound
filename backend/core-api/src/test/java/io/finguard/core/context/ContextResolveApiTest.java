package io.finguard.core.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.Map;

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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.JsonNode;

import io.finguard.core.agentrun.AgentRunLauncher;
import io.finguard.core.risk.PromptRiskModel;
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

    /**
     * Agent 실행 지시를 태우지 않는다. 이 테스트가 보는 것은 Context 해석이고, 실제 Agent가 없으면
     * Launcher가 AgentRun을 FAILED로 바꿔 그다음 resolve가 정당하게 거부된다 — 여기서 확인할 동작이 아니다.
     */
    @MockitoBean
    private AgentRunLauncher agentRunLauncher;

    /** REQUEST_ID를 테스트끼리 공유하므로 앞 테스트가 남긴 감사행을 지운다. */
    @BeforeEach
    void clearAuditEvents() {
        jdbcTemplate.update("delete from audit_event_requested_data");
        jdbcTemplate.update("delete from audit_event_reason_codes");
        jdbcTemplate.update("delete from audit_events");
    }

    @Test
    void returnsAllNineStatusesAndTheNotEvaluatedPromptSnapshot() {
        RunReferences run = startAgentRun();
        createAudit(run);

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
        // 감사행을 호출자 명의로 만든다. 소유권은 맞고 Passport의 Agent만 다른 상태를 만들어야
        // "헤더를 쓰는가"만 따로 볼 수 있다. 남의 행에 쓰는 것은 아래 테스트가 따로 막는다.
        createAudit(run, "SPOOFED-AGENT");

        ResponseEntity<JsonNode> response =
                resolve(
                        run,
                        "SPOOFED-AGENT",
                        "CUST-1001",
                        "CREDIT_SCORE_READ",
                        "CREDIT_SCORE");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        // 본문은 LOAN-AGENT-01이라고 주장하지만 헤더가 SPOOFED-AGENT다. 헤더를 썼다면 위반이다.
        assertThat(response.getBody().get("scopeStatus").get("agentBinding").asText())
                .isEqualTo("VIOLATION");
    }

    @Test
    void refusesToWriteEvidenceOntoAnotherAgentsAuditRow() {
        RunReferences run = startAgentRun();
        createAudit(run, "LOAN-AGENT-01");

        // 다른 Agent가 같은 requestId·agentRunId로 근거를 적으려 한다. agentRunId만 대조하면
        // 통과한다 — 둘 다 요청 본문이 고르는 값이기 때문이다.
        ResponseEntity<JsonNode> spoofed =
                resolve(run, "SPOOFED-AGENT", "CUST-1001", "CREDIT_SCORE_READ", "CREDIT_SCORE");

        assertThat(spoofed.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // 기존 행이 손대지지 않아야 한다. 근거는 set-once라 한 번 적히면 되돌릴 수 없다.
        Map<String, Object> untouched =
                jdbcTemplate.queryForMap(
                        "select agent_id, passport_id, scope_agent_binding"
                                + " from audit_events where request_id = ?",
                        REQUEST_ID);
        assertThat(untouched.get("agent_id")).isEqualTo("LOAN-AGENT-01");
        assertThat(untouched.get("passport_id")).isNull();
        assertThat(untouched.get("scope_agent_binding")).isNull();

        // 그리고 원래 주인은 여전히 자기 근거를 남길 수 있어야 한다. 이게 막히면 스푸핑 한 번으로
        // 남의 실행을 영구히 봉쇄할 수 있다.
        ResponseEntity<JsonNode> owner =
                resolve(run, "LOAN-AGENT-01", "CUST-1001", "CREDIT_SCORE_READ", "CREDIT_SCORE");

        assertThat(owner.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(
                        jdbcTemplate.queryForMap(
                                        "select passport_id from audit_events where request_id = ?",
                                        REQUEST_ID)
                                .get("passport_id"))
                .isEqualTo(run.passportId());
    }

    @Test
    void writesTheResolvedEvidenceOntoTheAuditRow() {
        RunReferences run = startAgentRun();
        createAudit(run);

        assertThat(resolve(run, "LOAN-AGENT-01", "CUST-9999", "CREDIT_SCORE_READ", "CREDIT_SCORE")
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);

        // Resolver가 계산한 근거가 감사 기록에 남아야 한다. 남지 않으면 나중에 "무엇을 보고
        // 그렇게 판단했는가"에 답할 수 없다 — contracts/audit/audit-event.schema.json.
        Map<String, Object> row =
                jdbcTemplate.queryForMap(
                        "select employee_id, passport_id, scope_customer_scope, scope_tool_scope,"
                                + " prompt_risk_evaluation_status, prompt_model_version"
                                + " from audit_events where request_id = ?",
                        REQUEST_ID);

        assertThat(row.get("employee_id")).isEqualTo("EMP-101");
        assertThat(row.get("passport_id")).isEqualTo(run.passportId());
        // 요청이 CUST-9999를 노렸으므로 고객 범위는 위반이다. 이 값이 OK로 남으면 증거가 거짓이 된다.
        assertThat(row.get("scope_customer_scope")).isEqualTo("VIOLATION");
        assertThat(row.get("scope_tool_scope")).isEqualTo("OK");
        assertThat(row.get("prompt_risk_evaluation_status")).isEqualTo("NOT_EVALUATED");
        // 리터럴로 박으면 모델을 재학습해 버전을 올릴 때마다 이 줄만 조용히 어긋난다.
        assertThat(row.get("prompt_model_version")).isEqualTo(PromptRiskModel.CURRENT_VERSION);

        assertThat(
                        jdbcTemplate.queryForObject(
                                "select data_type from audit_event_requested_data d"
                                        + " join audit_events a on a.audit_event_id = d.audit_event_id"
                                        + " where a.request_id = ?",
                                String.class,
                                REQUEST_ID))
                .isEqualTo("CREDIT_SCORE");
    }

    @Test
    void refusesToResolveWhenNoAuditRowIsWaitingForTheEvidence() {
        RunReferences run = startAgentRun();
        // 감사행을 만들지 않는다. docs/02:143-149 순서를 어긴 호출이다.

        ResponseEntity<JsonNode> response =
                resolve(run, "LOAN-AGENT-01", "CUST-1001", "CREDIT_SCORE_READ", "CREDIT_SCORE");

        // 통과시키면 Core가 인가 근거를 반환하는데 그 근거를 받을 행이 없다.
        // 남는 흔적은 만료되는 로그뿐이므로 fail-closed로 막는다.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    /**
     * requestId는 요청 본문에서 온다. 그 값으로 찾은 감사행이 지금 해석 중인 실행의 것인지 보지
     * 않으면, 남의 행에 내 Passport·Employee·Scope를 적을 수 있다. 게다가 한 번 적히면 set-once라
     * 원래 주인은 자기 근거를 영영 남기지 못한다 — 증거 위조이면서 동시에 봉쇄다.
     */
    @Test
    void refusesToWriteEvidenceOntoAnAuditRowThatBelongsToAnotherRun() {
        RunReferences owner = startAgentRun();
        createAudit(owner);
        RunReferences other = startAgentRun();

        ResponseEntity<JsonNode> response =
                resolve(other, "LOAN-AGENT-01", "CUST-1001", "CREDIT_SCORE_READ", "CREDIT_SCORE");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        // 주인의 행은 손대지 않은 채로 남아야 한다.
        Map<String, Object> row =
                jdbcTemplate.queryForMap(
                        "select agent_run_id, passport_id from audit_events where request_id = ?",
                        REQUEST_ID);
        assertThat(row.get("agent_run_id")).isEqualTo(owner.agentRunId());
        assertThat(row.get("passport_id")).isNull();
    }

    @Test
    void reportsAMissingPassportAsTaskPassportNotFound() {
        RunReferences missing = new RunReferences("RUN-MISSING", "PASS-MISSING", "LOAN-2026-001");

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
        RunReferences missingRun = new RunReferences("RUN-MISSING", run.passportId(), run.caseId());

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
        createAudit(run);

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
        createAudit(run);

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
                response.getBody().get("passportId").asText(),
                response.getBody().get("caseId").asText());
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

    private record RunReferences(String agentRunId, String passportId, String caseId) {
    }

    /**
     * 선저장 Business Audit. {@code docs/02-architecture.md}:143-149의 순서상 감사행이 먼저 생기고
     * resolve가 뒤에 온다. resolve가 근거를 그 행에 적으므로 성공 경로 테스트에는 이 행이 있어야 한다.
     */
    private void createAudit(RunReferences run) {
        createAudit(run, "LOAN-AGENT-01");
    }

    /**
     * 감사행의 소유 Agent를 정해서 만든다. 저장되는 {@code agent_id}는 본문이 아니라
     * {@code X-Verified-Agent-Id} 헤더에서 온다({@code AuditService.create}).
     */
    private void createAudit(RunReferences run, String owningAgentId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(InternalCredentialFilter.CREDENTIAL_HEADER, "test-internal-credential");
        headers.set("X-Verified-Agent-Id", owningAgentId);
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
                                  "verifiedAgentId": "LOAN-AGENT-01",
                                  "caseId": "%s",
                                  "targetConsumerId": "CUST-1001",
                                  "requestedTool": "CREDIT_SCORE_READ",
                                  "status": "PROCESSING",
                                  "requestedAt": "2026-08-25T12:00:00Z"
                                }
                                """
                                        .formatted(REQUEST_ID, run.agentRunId(), run.caseId()),
                                headers),
                        JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }
}
