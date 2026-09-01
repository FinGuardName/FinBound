dependencies {
    testImplementation("com.networknt:json-schema-validator:2.0.4")
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.12.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.12.2")
}

tasks.test {
    systemProperty("finguard.repository.root", rootProject.projectDir.absolutePath)
}
