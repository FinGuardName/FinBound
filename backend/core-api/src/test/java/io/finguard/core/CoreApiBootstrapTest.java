package io.finguard.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.Location;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * core-api가 실제 PostgreSQL 위에서 기동되는지 고정한다.
 *
 * <p>{@code ddl-auto: validate}는 스키마를 만들지 않는다. 스키마의 주인은 {@code db/migration}의
 * 마이그레이션이다 — docs/adr/0002-flyway-owns-core-api-schema.md.
 */
@SpringBootTest(
        properties = {
            "finguard.internal.credential=test-internal-credential",
            "finguard.api.viewer-credential=test-viewer-credential",
            "finguard.api.operator-credential=test-operator-credential",
            "finguard.api.operator-employee-id=EMP-101",
        })
@Testcontainers
class CoreApiBootstrapTest {

    @Container
    @ServiceConnection
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.10-alpine");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Flyway flyway;

    @Test
    void flywayAppliesTheBaselineAndOwnsSchemaHistory() {
        Integer requiredTables =
                jdbcTemplate.queryForObject(
                        "select count(*) from information_schema.tables"
                                + " where table_schema = current_schema()"
                                + " and table_name in ('flyway_schema_history', 'employees', 'audit_events')",
                        Integer.class);

        assertThat(requiredTables)
                .as("Flyway 이력과 baseline의 대표 업무 테이블이 함께 생겨야 한다")
                .isEqualTo(3);
    }

    @Test
    void demoSeedIsNotLoadedOutsideTheLocalProfile() {
        // 데모 시드(EMP-101, CUST-1001/9999 …)가 local 밖으로 새면 안 된다.
        // 이 단언은 누군가 db/local을 기본 locations에 넣는 순간 깨진다.
        assertThat(flyway.getConfiguration().getLocations())
                .extracting(Location::getDescriptor)
                .noneMatch(descriptor -> descriptor.contains("db/local"));
    }
}
