package io.finguard.core.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * {@code /api/v1/**} 인증 설정의 기동 검증. {@code docs/04-api-contract.md} §2 —
 * "두 Credential은 필수이며 비어 있거나 서로 같으면 Core 기동에 실패한다."
 *
 * <p>설정 실수로 권한 경계가 조용히 무너지는 경로라 기동을 막는다. 검증이 도는지 자체를 확인하지
 * 않으면 {@code @AssertTrue} 메서드 이름을 잘못 지어도 아무도 모른다 — 검증이 안 도는 것과 통과하는
 * 것은 겉으로 같아 보인다.
 */
class CoreApiPropertiesTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(PropertiesOnly.class);

    @Test
    void startsWhenBothCredentialsArePresentAndDiffer() {
        runner.withPropertyValues(
                        "finguard.api.viewer-credential=viewer-secret",
                        "finguard.api.operator-credential=operator-secret",
                        "finguard.api.operator-employee-id=EMP-101")
                .run(context -> assertThat(context).hasNotFailed());
    }

    /**
     * 두 Credential이 같으면 역할 구분이 사라져 Viewer가 AgentRun을 생성할 수 있게 된다.
     *
     * <p>이건 배포 설정 한 줄로 권한 경계가 통째로 없어지는 경로다. 런타임에 드러나지 않고 조용히
     * 열리므로 기동에서 막는다.
     */
    @Test
    void failsToStartWhenTheTwoCredentialsAreTheSame() {
        runner.withPropertyValues(
                        "finguard.api.viewer-credential=same-secret",
                        "finguard.api.operator-credential=same-secret",
                        "finguard.api.operator-employee-id=EMP-101")
                .run(context -> {
                    // hasFailed()만 보면 무관한 이유로 깨져도 통과하므로 원인까지 단언한다.
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("CoreApiProperties")
                            .rootCause()
                            .hasMessageContaining("credentials must differ");
                });
    }

    @Test
    void failsToStartWhenTheOperatorEmployeeIdIsBlank() {
        runner.withPropertyValues(
                        "finguard.api.viewer-credential=viewer-secret",
                        "finguard.api.operator-credential=operator-secret",
                        "finguard.api.operator-employee-id=   ")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("CoreApiProperties")
                            .rootCause()
                            .hasMessageContaining("operatorEmployeeId");
                });
    }

    @Test
    void failsToStartWhenTheViewerCredentialIsMissing() {
        runner.withPropertyValues(
                        "finguard.api.operator-credential=operator-secret",
                        "finguard.api.operator-employee-id=EMP-101")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("CoreApiProperties")
                            .rootCause()
                            .hasMessageContaining("viewerCredential");
                });
    }

    @Test
    void failsToStartWhenTheOperatorCredentialIsMissing() {
        runner.withPropertyValues(
                        "finguard.api.viewer-credential=viewer-secret",
                        "finguard.api.operator-employee-id=EMP-101")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("CoreApiProperties")
                            .rootCause()
                            .hasMessageContaining("operatorCredential");
                });
    }

    /** 인증 배선 전체가 아니라 설정 검증만 본다. 웹 계층을 끌어오면 무관한 이유로 기동이 깨진다. */
    @Configuration
    @EnableConfigurationProperties(CoreApiProperties.class)
    static class PropertiesOnly {
    }
}
