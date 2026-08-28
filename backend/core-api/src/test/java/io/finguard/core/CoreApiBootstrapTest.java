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
@SpringBootTest(properties = "finguard.internal.credential=test-internal-credential")
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

    // 마이그레이션 파일이 아직 없으므로 이 단언이 증명하는 것은 "Flyway가 배선돼 스키마 이력을
    // 소유한다"까지다. 실제 테이블 생성 검증은 baseline이 들어오는 이슈 #18에서 한다.
    @Test
    void flywayIsWiredAndOwnsSchemaHistory() {
        Integer historyTables =
                jdbcTemplate.queryForObject(
                        "select count(*) from information_schema.tables where table_name = 'flyway_schema_history'",
                        Integer.class);

        assertThat(historyTables)
                .as("Flyway가 스키마를 소유하면 이력 테이블이 생긴다")
                .isEqualTo(1);
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
