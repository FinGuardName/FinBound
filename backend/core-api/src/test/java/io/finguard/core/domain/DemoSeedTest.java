package io.finguard.core.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * local 프로파일에서 데모 시드가 실제로 적재되는지 확인한다.
 *
 * <p>재현하려는 장면은 "EMP-101은 CUST-9999를 볼 권한이 있는데 현재 업무 대상이 CUST-1001이라
 * 막힌다"이다. 이 전제가 데이터에 갖춰져 있지 않으면 이슈 #20의 Resolver가 무엇을 계산하든
 * 데모가 성립하지 않는다.
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
class DemoSeedTest {

    @Container
    @ServiceConnection
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.10-alpine");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Flyway flyway;

    @Test
    void employeeAuthorityIsBroad() {
        // 데모의 전제. 직원 개인 권한이 좁으면 "권한은 있는데 막힌다"를 보여줄 수 없다.
        String scope =
                jdbcTemplate.queryForObject(
                        "select allowed_customer_scope from employee_authorities where employee_id = 'EMP-101'",
                        String.class);
        List<String> tools =
                jdbcTemplate.queryForList(
                        "select tool from employee_authority_allowed_tools where employee_id = 'EMP-101'",
                        String.class);

        assertThat(scope).isEqualTo("ALL");
        assertThat(tools).containsExactlyInAnyOrder("CREDIT_SCORE_READ", "INCOME_READ", "DEBT_READ");
    }

    @Test
    void blockedConsumerActuallyExists() {
        // CUST-9999가 없으면 "없는 고객이라 막혔다"가 되어 데모의 요점이 사라진다.
        Integer count =
                jdbcTemplate.queryForObject(
                        "select count(*) from consumers where consumer_id = 'CUST-9999'", Integer.class);

        assertThat(count).as("CUST-9999는 실재해야 한다").isEqualTo(1);
    }

    @Test
    void currentCaseTargetsOnlyTheOtherConsumer() {
        String consumerId =
                jdbcTemplate.queryForObject(
                        "select consumer_id from financial_cases where case_id = 'LOAN-2026-001'",
                        String.class);

        assertThat(consumerId).isEqualTo("CUST-1001");
    }

    @Test
    void currentCaseHasNotExpired() {
        // F04 예시의 2026-08-17 15:00 을 그대로 쓰면 시드를 넣는 순간 만료된 Case가 되고,
        // caseStatus = VIOLATION 때문에 customerScope 데모가 엉뚱한 이유로 실패한다.
        Instant expiresAt =
                jdbcTemplate.queryForObject(
                        "select expires_at from financial_cases where case_id = 'LOAN-2026-001'",
                        Instant.class);
        String status =
                jdbcTemplate.queryForObject(
                        "select status from financial_cases where case_id = 'LOAN-2026-001'", String.class);

        assertThat(status).isEqualTo("ACTIVE");
        assertThat(expiresAt).isAfter(Instant.now());
    }

    @Test
    void mandatesCoverTheCaseConsumerAndTheTwoAttackFixtures() {
        // CUST-1001은 데모의 정상 경로다. CUST-1002·CUST-1003은 공격 Scenario가 실제로 막히도록
        // Mandate를 좁혀 둔 Fixture다 — docs/04-api-contract.md §3.1. Mandate가 Data를 좁히면
        // EffectivePermissionCalculator가 그 Data를 요구하는 Tool까지 함께 떨어뜨린다.
        List<String> mandateConsumers =
                jdbcTemplate.queryForList("select consumer_id from consumer_mandates", String.class);

        assertThat(mandateConsumers)
                .containsExactlyInAnyOrder("CUST-1001", "CUST-1002", "CUST-1003");
        assertThat(allowedDataOf("CUST-1001"))
                .containsExactlyInAnyOrder("CREDIT_SCORE", "INCOME", "DEBT");
        assertThat(allowedDataOf("CUST-1002"))
                .as("TOOL/DATA 공격용 — INCOME이 빠져야 한다")
                .containsExactlyInAnyOrder("CREDIT_SCORE", "DEBT");
        assertThat(allowedDataOf("CUST-1003"))
                .as("MANDATE 공격용 — DEBT가 빠져야 한다")
                .containsExactlyInAnyOrder("CREDIT_SCORE", "INCOME");
    }

    private List<String> allowedDataOf(String consumerId) {
        return jdbcTemplate.queryForList(
                "select d.data_type from consumer_mandate_allowed_data d"
                        + " join consumer_mandates m on m.mandate_id = d.mandate_id"
                        + " where m.consumer_id = ?",
                String.class,
                consumerId);
    }

    @Test
    void taskPassportIsNotSeeded() {
        // Passport는 이슈 #19의 계산기가 발급해야 한다. 시드해두면 계산기가 망가져도 데모가 성공한다.
        Integer count =
                jdbcTemplate.queryForObject("select count(*) from task_passports", Integer.class);

        assertThat(count).as("Passport는 시드하지 않는다").isZero();
    }

    @Test
    void repeatableSeedRestoresOwnedRowsOnAnExistingDatabase() {
        Long previousAuthorityVersion =
                jdbcTemplate.queryForObject(
                        "select version from employee_authorities where employee_id = 'EMP-101'",
                        Long.class);

        jdbcTemplate.update(
                "update employee_authorities set status = 'INACTIVE' where employee_id = 'EMP-101'");
        jdbcTemplate.update(
                "delete from employee_authority_allowed_tools"
                        + " where employee_id = 'EMP-101' and tool = 'DEBT_READ'");
        jdbcTemplate.update(
                "update financial_cases set status = 'CANCELLED', consumer_id = 'CUST-9999'"
                        + " where case_id = 'LOAN-2026-001'");

        // Flyway가 변경된 Repeatable migration을 다시 적용한 것과 같은 상태를 만든다.
        int deletedHistoryRows =
                jdbcTemplate.update(
                        "delete from flyway_schema_history"
                                + " where version is null and script = 'R__demo_seed.sql'");
        assertThat(deletedHistoryRows).isEqualTo(1);
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);

        String authorityStatus =
                jdbcTemplate.queryForObject(
                        "select status from employee_authorities where employee_id = 'EMP-101'",
                        String.class);
        Long currentAuthorityVersion =
                jdbcTemplate.queryForObject(
                        "select version from employee_authorities where employee_id = 'EMP-101'",
                        Long.class);
        List<String> tools =
                jdbcTemplate.queryForList(
                        "select tool from employee_authority_allowed_tools where employee_id = 'EMP-101'",
                        String.class);
        String caseStatus =
                jdbcTemplate.queryForObject(
                        "select status from financial_cases where case_id = 'LOAN-2026-001'",
                        String.class);
        String caseConsumer =
                jdbcTemplate.queryForObject(
                        "select consumer_id from financial_cases where case_id = 'LOAN-2026-001'",
                        String.class);

        assertThat(authorityStatus).isEqualTo("ACTIVE");
        assertThat(currentAuthorityVersion).isGreaterThan(previousAuthorityVersion);
        assertThat(tools).containsExactlyInAnyOrder("CREDIT_SCORE_READ", "INCOME_READ", "DEBT_READ");
        assertThat(caseStatus).isEqualTo("ACTIVE");
        assertThat(caseConsumer).isEqualTo("CUST-1001");
    }
}
