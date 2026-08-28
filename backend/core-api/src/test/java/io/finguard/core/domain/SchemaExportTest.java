package io.finguard.core.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 엔티티 매핑에서 DDL 초안을 뽑는다. ADR 0002가 정한 baseline 작성 방식이다.
 *
 * <p>여기서 나온 파일을 사람이 검토·수정해서 {@code db/migration/V1__baseline.sql}로 만든다.
 * 런타임에는 Hibernate가 스키마를 만들지 않는다({@code ddl-auto: validate}).
 *
 * <p>초안 생성용이므로 CI에서 매번 돌 필요는 없다. 매핑을 바꾼 뒤 baseline을 갱신할 때 다시 쓴다.
 */
@SpringBootTest(
        properties = {
            "finguard.internal.credential=schema-export",
            "spring.jpa.hibernate.ddl-auto=none",
            "spring.flyway.enabled=false",
            "spring.jpa.properties.jakarta.persistence.schema-generation.scripts.action=create",
            "spring.jpa.properties.jakarta.persistence.schema-generation.scripts.create-target="
                    + SchemaExportTest.TARGET,
        })
@Testcontainers
class SchemaExportTest {

    static final String TARGET = "build/generated-schema.sql";

    @Container
    @ServiceConnection
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.10-alpine");

    @Test
    void exportsDdlForEveryMappedEntity() throws Exception {
        String ddl = Files.readString(Path.of(TARGET));

        assertThat(ddl)
                .contains("create table employees")
                .contains("create table employee_authorities")
                .contains("create table consumers")
                .contains("create table consumer_mandates")
                .contains("create table permission_templates")
                .contains("create table financial_cases")
                .contains("create table task_passports")
                .contains("create table agent_runs")
                .contains("create table secured_agent_inputs")
                .contains("create table prompt_risk_snapshots")
                .contains("create table audit_events")
                .contains("create table security_auth_events");
    }
}
