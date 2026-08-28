plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-web")
    // 스키마의 주인은 db/migration이다. ddl-auto는 validate로 두고 Hibernate가 스키마를 만들지 않는다.
    // docs/adr/0002-flyway-owns-core-api-schema.md
    implementation("org.flywaydb:flyway-core")
    // Flyway 10부터 DB별 지원이 별도 모듈로 분리됐다.
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // 영속화 테스트는 실제 PostgreSQL에서 돌린다. H2는 array/JSONB/timestamptz 동작이 다르고,
    // create-drop은 스키마 정의를 둘로 갈라 배포되지 않는 스키마 위에서 테스트가 통과하게 만든다.
    // docs/adr/0002-flyway-owns-core-api-schema.md
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
}
