package io.finguard.core.agentrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.finguard.core.domain.TaskType;
import io.finguard.core.permission.PermissionNotIssuableException;
import jakarta.persistence.EntityManager;

/**
 * AgentRun 시작과 Task Passport 발급. {@code docs/04-api-contract.md} §3.
 *
 * <p>데모 시드 위에서 돌린다. EMP-101은 모든 고객을 볼 수 있지만 CUST-1001로 업무를 시작하면
 * 그 Passport는 CUST-1001까지만 허용해야 한다 — 이번 사이클 데모의 앞쪽 절반이다.
 *
 * <p>{@code @Transactional}로 매 테스트를 되돌린다. 그러지 않으면 실행이 한 컨테이너에 쌓이고,
 * PromptRiskSnapshot은 {@code inputHash}로 공유되므로 <strong>다른 테스트가 만든 행을 찾아
 * 통과하는</strong> 일이 생긴다. 그러면 이 파일의 단언들이 무엇도 증명하지 못한다.
 */
@SpringBootTest(
        properties = {
            "finguard.internal.credential=test-internal-credential",
            "finguard.api.viewer-credential=test-viewer-credential",
            "finguard.api.operator-credential=test-operator-credential",
            "finguard.api.operator-employee-id=EMP-101",
        })
@ActiveProfiles("local")
@Testcontainers
@Transactional
class AgentRunServiceTest {

    @Container
    @ServiceConnection
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.10-alpine");

    @Autowired
    private AgentRunService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    /**
     * 서비스를 부르고 영속성 컨텍스트를 내린다.
     *
     * <p>{@code jdbcTemplate}은 JPA를 거치지 않으므로 flush 전에는 컬렉션 테이블 행을 보지 못한다.
     * 테스트가 트랜잭션 안에서 돌기 때문에 필요한 절차다.
     */
    private AgentRunStarted start(String consumerId, String inputText) {
        AgentRunStarted started =
                service.start("EMP-101", consumerId, TaskType.LOAN_REVIEW, inputText);
        entityManager.flush();
        return started;
    }

    @Test
    void issuesPassportScopedToTheRequestedConsumer() {
        AgentRunStarted started =
                start("CUST-1001", "CUST-1001의 대출심사를 진행해줘.");

        String consumerId =
                jdbcTemplate.queryForObject(
                        "select consumer_id from task_passports where passport_id = ?",
                        String.class,
                        started.passportId());
        List<String> tools =
                jdbcTemplate.queryForList(
                        "select tool from task_passport_allowed_tools where passport_id = ?",
                        String.class,
                        started.passportId());

        assertThat(consumerId).isEqualTo("CUST-1001");
        assertThat(tools).containsExactlyInAnyOrder("CREDIT_SCORE_READ", "INCOME_READ", "DEBT_READ");
    }

    @Test
    void recordsTheSourceVersionsThePassportWasIssuedAgainst() {
        AgentRunStarted started =
                start("CUST-1001", "대출심사 진행");

        // 원본 버전이 바뀌면 이 Passport는 TASK_PASSPORT_STALE 이 되어야 한다.
        // 그러려면 발급 시점의 값이 실제로 박혀 있어야 한다.
        Long employeeAuthorityVersion =
                jdbcTemplate.queryForObject(
                        "select source_version_employee_authority from task_passports where passport_id = ?",
                        Long.class,
                        started.passportId());
        Long currentVersion =
                jdbcTemplate.queryForObject(
                        "select version from employee_authorities where employee_id = 'EMP-101'", Long.class);

        assertThat(employeeAuthorityVersion).isEqualTo(currentVersion);
    }

    @Test
    void storesOnlyTheHashOfTheInput() {
        String rawInput = "CUST-1001의 대출심사를 진행해줘.";

        AgentRunStarted started = start("CUST-1001", rawInput);

        String inputHash =
                jdbcTemplate.queryForObject(
                        "select i.input_hash from secured_agent_inputs i where i.agent_run_id = ?",
                        String.class,
                        started.agentRunId());

        assertThat(inputHash).startsWith("sha256:");
        // docs/06 §24 — 원본 Prompt를 저장하지 않는다. 어느 컬럼에도 원문이 있으면 안 된다.
        Integer rawTextRows =
                jdbcTemplate.queryForObject(
                        "select count(*) from secured_agent_inputs where input_hash like ?",
                        Integer.class,
                        "%" + rawInput + "%");
        assertThat(rawTextRows).isZero();
    }

    @Test
    void doesNotInventTheContentLanguage() {
        AgentRunStarted started =
                start("CUST-1001", "Please review the loan.");

        String contentLanguage =
                jdbcTemplate.queryForObject(
                        "select content_language from secured_agent_inputs where agent_run_id = ?",
                        String.class,
                        started.agentRunId());

        // 판별하지 않았으므로 비어 있어야 한다. 감사 시스템에서 없는 사실을 만들어내는 것은 null 보다 나쁘다.
        assertThat(contentLanguage).isNull();
    }

    @Test
    void recordsPromptRiskAsNotEvaluated() {
        AgentRunStarted started = start("CUST-1001", "대출심사 진행");

        String evaluationStatus =
                jdbcTemplate.queryForObject(
                        // 스냅샷은 입력 해시로 공유되므로 input_ref 가 아니라 input_hash 로 잇는다.
                        "select s.evaluation_status from prompt_risk_snapshots s"
                                + " join secured_agent_inputs i on i.input_hash = s.input_hash"
                                + " where i.agent_run_id = ?",
                        String.class,
                        started.agentRunId());

        // Detector를 부르지 않았으므로 "검사했고 음성"이라고 기록하면 거짓이 된다. docs/04 §7.
        assertThat(evaluationStatus).isEqualTo("NOT_EVALUATED");
    }

    @Test
    void agentRunStartsAsRunningAndReferencesThePassport() {
        AgentRunStarted started = start("CUST-1001", "대출심사 진행");

        String status =
                jdbcTemplate.queryForObject(
                        "select status from agent_runs where agent_run_id = ?",
                        String.class,
                        started.agentRunId());
        String passportId =
                jdbcTemplate.queryForObject(
                        "select passport_id from agent_runs where agent_run_id = ?",
                        String.class,
                        started.agentRunId());

        assertThat(status).isEqualTo("RUNNING");
        assertThat(passportId).isEqualTo(started.passportId());
    }

    @Test
    void refusesWhenTheConsumerGaveNoMandate() {
        // CUST-9999는 실재하지만 LOAN_REVIEW 동의가 없다. 권한이 넓어도 여기서 멈춰야 한다.
        assertThatThrownBy(
                        () -> service.start("EMP-101", "CUST-9999", TaskType.LOAN_REVIEW, "대출심사 진행"))
                .isInstanceOf(PermissionNotIssuableException.class)
                .extracting("reasonCode")
                .isEqualTo("MANDATE_NOT_FOUND");
    }
}
