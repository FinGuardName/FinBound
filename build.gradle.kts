plugins {
    id("org.springframework.boot") version "3.5.16" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    id("org.sonarqube") version "6.0.1.5171"
    java
}

allprojects {
    group = "io.finguard"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

// 08-git-collaboration-convention.md §12.2에 따른 Coverage 제외 대상.
// Spring Boot 부트스트랩 클래스는 같은 절이 제외를 허용한 Configuration에 해당한다.
// 제외 대상을 넓히려면 팀 합의를 거친다.
val coverageExclusions = listOf(
    "**/*Application.class",
)
val coverageSourceExclusions = coverageExclusions.map { it.removeSuffix(".class") + ".java" }

subprojects {
    apply(plugin = "java")
    apply(plugin = "jacoco")
    apply(plugin = "checkstyle")

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    // §14 NAVER HackDay Java Convention.
    // 룰셋의 severity는 warning이므로 Gradle 빌드를 세우지 않고,
    // 결과는 SonarQube로 넘겨 §13 Quality Gate에서 판정한다.
    extensions.configure<CheckstyleExtension> {
        toolVersion = "10.21.4"
        configFile = rootProject.file("naver-checkstyle.xml")
        configProperties = mapOf(
            "suppressionFile" to rootProject.file("naver-checkstyle-suppressions.xml").absolutePath,
        )
    }

    tasks.withType<Checkstyle>().configureEach {
        reports {
            xml.required.set(true)
            html.required.set(false)
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()

        // 진단용 임시 설정. 기본값은 예외 메시지를 생략해 CI 로그로 원인을 볼 수 없다.
        testLogging {
            showStandardStreams = true
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }

        // Windows에서 사용자 이름이 ASCII가 아니면 기본 java.io.tmpdir가
        // C:\Users\<한글>\AppData\Local\Temp 가 된다. byte-buddy는 임시 디렉터리에 에이전트 jar를
        // 만들어 자기 프로세스에 붙이는데 그 경로가 non-ASCII면 부착이 실패하고,
        // Mockito가 "JDK does not supply a working agent attachment mechanism"으로 죽는다.
        // Spring Boot의 ResetMocksTestExecutionListener는 mock을 쓰지 않는 @SpringBootTest에서도
        // Mockito를 초기화하므로, 이 경우 통합 테스트 전체가 실행되지 못한다.
        // 테스트 JVM의 임시 디렉터리를 프로젝트 안(ASCII 경로)으로 고정해 회피한다.
        val testTmpDir = layout.buildDirectory.dir("tmp/test-jvm").get().asFile
        doFirst {
            testTmpDir.mkdirs()
        }
        systemProperty("java.io.tmpdir", testTmpDir.absolutePath)

        // 테스트 JVM은 Gradle의 file.encoding을 물려받지 않는다. 한글 단언 메시지가 리포트에서 깨진다.
        defaultCharacterEncoding = "UTF-8"

        finalizedBy(tasks.named("jacocoTestReport"))
    }

    tasks.named<JacocoReport>("jacocoTestReport") {
        dependsOn(tasks.named("test"))
        // SonarQube가 읽는 형식.
        reports {
            xml.required.set(true)
            html.required.set(false)
        }
        classDirectories.setFrom(
            files(classDirectories.files.map { fileTree(it) { exclude(coverageExclusions) } }),
        )
    }

    // §12.2 Coverage < 80% → PR Merge 제한.
    tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
        dependsOn(tasks.named("test"))
        classDirectories.setFrom(
            files(classDirectories.files.map { fileTree(it) { exclude(coverageExclusions) } }),
        )
        violationRules {
            rule {
                limit {
                    counter = "LINE"
                    value = "COVEREDRATIO"
                    minimum = "0.80".toBigDecimal()
                }
            }
        }
    }

    // 테스트 소스가 하나도 없으면 test가 NO-SOURCE가 되고 jacocoTestCoverageVerification이
    // SKIPPED로 넘어간다. 즉 테스트를 전혀 쓰지 않으면 Coverage 게이트를 그냥 통과한다.
    // §18 "테스트 없는 기능 PR 금지"를 지키려면 그 경로를 막아야 한다.
    val enforceTestPresence = tasks.register("enforceTestPresence") {
        group = "verification"
        description = "Coverage 대상 production 코드가 있는데 테스트 소스가 없으면 실패한다."

        val sourceSets = project.extensions.getByType<SourceSetContainer>()
        val coveredMainSources = sourceSets["main"].allJava.matching {
            exclude(coverageSourceExclusions)
        }
        val testSources = sourceSets["test"].allJava
        val projectPath = path

        inputs.files(coveredMainSources, testSources)
        doLast {
            if (!coveredMainSources.isEmpty && testSources.isEmpty) {
                throw GradleException(
                    "$projectPath: Coverage 대상 production 코드가 있으나 테스트 소스가 없습니다. " +
                        "docs/08-git-collaboration-convention.md §18 — 테스트 없는 기능 PR 금지",
                )
            }
        }
    }

    tasks.named("check") {
        dependsOn(tasks.named("jacocoTestCoverageVerification"), enforceTestPresence)
    }
}

// §13 SonarQube. projectKey/organization은 SonarCloud에서 저장소를 import한 뒤
// 발급되는 값이며, 다르면 CI에서 인증 실패한다.
sonar {
    properties {
        property("sonar.projectKey", "FinGuardName_finguard")
        property("sonar.organization", "finguardname")
        property("sonar.host.url", "https://sonarcloud.io")
    }
}

subprojects {
    sonar {
        properties {
            property(
                "sonar.coverage.jacoco.xmlReportPaths",
                layout.buildDirectory.file("reports/jacoco/test/jacocoTestReport.xml").get().asFile.path,
            )
            property(
                "sonar.java.checkstyle.reportPaths",
                layout.buildDirectory.file("reports/checkstyle/main.xml").get().asFile.path,
            )
            property("sonar.coverage.exclusions", "**/*Application.java")
        }
    }
}
