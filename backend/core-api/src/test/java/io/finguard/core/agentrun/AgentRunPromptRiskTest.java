package io.finguard.core.agentrun;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.finguard.core.domain.AgentSimulationScenario;
import io.finguard.core.domain.PromptRiskLevel;
import io.finguard.core.domain.TaskType;
import io.finguard.core.risk.PromptRiskEvaluation;
import io.finguard.core.risk.PromptRiskModel;
import io.finguard.core.risk.RequestTrace;
import jakarta.persistence.EntityManager;

/**
 * 평가 결과가 스냅샷에 실제로 실리는지. 이슈 #96.
 *
 * <p>{@code @Transactional} 로 되돌린다. 스냅샷은 {@code inputHash} 로 공유되므로 남겨 두면
 * 다른 테스트가 만든 행을 찾아 통과하는 일이 생긴다 — {@code AgentRunServiceTest} 가 같은 이유로
 * 같은 짝을 쓴다.
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
class AgentRunPromptRiskTest {

    @Container
    @ServiceConnection
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.10-alpine");

    @Autowired
    private AgentRunService service;

    @Autowired
    private AgentRunPreparer preparer;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 승격은 managed 엔티티를 고쳐 dirty checking 에 맡긴다. 운영에서는 커밋이 flush 하지만
     * 이 테스트는 같은 트랜잭션 안에서 JdbcTemplate 으로 바로 읽으므로 직접 flush 해야 보인다 —
     * {@code AgentRunServiceTest} 가 같은 이유로 같은 짝을 쓴다.
     */
    @Autowired
    private EntityManager entityManager;

    /** 실제 Agent 를 태우지 않는다. 이 테스트가 보는 것은 스냅샷이다. */
    @MockitoBean
    private AgentRunLauncher launcher;

    private PromptRiskEvaluation critical() {
        return new PromptRiskEvaluation(
                true, new BigDecimal("0.9600"), PromptRiskLevel.CRITICAL, null,
                Set.of("IGNORE_PREVIOUS_INSTRUCTION"), PromptRiskModel.CURRENT_VERSION,
                java.time.Instant.parse("2026-09-04T00:00:00Z"));
    }

    private String start(String inputText, Optional<PromptRiskEvaluation> evaluation) {
        PreparedAgentRun base = preparer.prepare(inputText, RequestTrace.of(null, null));
        PreparedAgentRun prepared =
                new PreparedAgentRun(base.agentRunId(), base.inputRef(), base.inputHash(), evaluation);
        service.start(
                "EMP-101",
                "CUST-1001",
                TaskType.LOAN_REVIEW,
                prepared,
                AgentSimulationScenario.NORMAL_CREDIT_SCORE);
        entityManager.flush();
        return prepared.inputHash();
    }

    @Test
    void storesTheEvaluationOnTheSnapshot() {
        String hash = start("평가되는 입력 " + System.nanoTime(), Optional.of(critical()));

        assertThat(status(hash)).isEqualTo("EVALUATED");
        assertThat(
                        jdbcTemplate.queryForObject(
                                "select risk_level from prompt_risk_snapshots where input_hash = ?",
                                String.class, hash))
                .isEqualTo("CRITICAL");
        assertThat(
                        jdbcTemplate.queryForObject(
                                "select detected from prompt_risk_snapshots where input_hash = ?",
                                Boolean.class, hash))
                .isTrue();
    }

    @Test
    void leavesTheSnapshotNotEvaluatedWhenTheDetectorGaveNothing() {
        String hash = start("평가 실패 입력 " + System.nanoTime(), Optional.empty());

        assertThat(status(hash)).isEqualTo("NOT_EVALUATED");
        assertThat(rowCount(hash)).isEqualTo(1);
    }

    @Test
    void promotesTheExistingRowInsteadOfInsertingASecondOne() {
        // 장애로 NOT_EVALUATED 가 남은 뒤 같은 입력이 다시 오면 같은 행이 승격되어야 한다.
        // 새로 INSERT 하면 uk_prompt_risk_input_hash_model 에 걸려 실행 전체가 롤백된다.
        String text = "재평가 입력 " + System.nanoTime();
        String hash = start(text, Optional.empty());
        assertThat(status(hash)).isEqualTo("NOT_EVALUATED");

        start(text, Optional.of(critical()));

        assertThat(status(hash)).isEqualTo("EVALUATED");
        assertThat(rowCount(hash)).isEqualTo(1);
    }

    private String status(String inputHash) {
        return jdbcTemplate.queryForObject(
                "select evaluation_status from prompt_risk_snapshots where input_hash = ?",
                String.class, inputHash);
    }

    private Integer rowCount(String inputHash) {
        return jdbcTemplate.queryForObject(
                "select count(*) from prompt_risk_snapshots where input_hash = ?",
                Integer.class, inputHash);
    }
}
