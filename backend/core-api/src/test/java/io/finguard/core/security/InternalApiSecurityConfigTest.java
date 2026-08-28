package io.finguard.core.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

class InternalApiSecurityConfigTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(InternalApiSecurityConfig.class);

    @Test
    void registersCredentialFilterForInternalPathsOnly() {
        runner.withPropertyValues("finguard.internal.credential=s3cret")
                .run(context -> {
                    assertThat(context).hasSingleBean(FilterRegistrationBean.class);
                    FilterRegistrationBean<?> registration =
                            context.getBean(FilterRegistrationBean.class);
                    assertThat(registration.getFilter()).isInstanceOf(InternalCredentialFilter.class);
                    assertThat(registration.getUrlPatterns()).containsExactly("/internal/*");
                });
    }

    @Test
    void failsToStartWhenCredentialIsNotConfigured() {
        // fail-closed: 자격 증명이 없으면 인증 없는 Internal API가 떠 있는 상태가 되면 안 된다.
        // hasFailed()만 보면 무관한 이유로 컨텍스트가 깨져도 통과하므로 실패 원인까지 단언한다.
        runner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasMessageContaining("InternalApiProperties")
                    .rootCause()
                    .hasMessageContaining("credential");
        });
    }

    @Test
    void failsToStartWhenCredentialIsBlank() {
        runner.withPropertyValues("finguard.internal.credential=   ")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("InternalApiProperties")
                            .rootCause()
                            .hasMessageContaining("credential");
                });
    }
}
