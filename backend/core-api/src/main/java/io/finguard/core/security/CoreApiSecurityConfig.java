package io.finguard.core.security;

import java.util.EnumSet;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import jakarta.servlet.DispatcherType;

/** 브라우저(Vue) → Core {@code /api/v1/**} 인증 배선. {@code docs/04-api-contract.md} §2. */
@Configuration
@EnableConfigurationProperties(CoreApiProperties.class)
public class CoreApiSecurityConfig {

    @Bean
    FilterRegistrationBean<CoreApiCredentialFilter> coreApiCredentialFilterRegistration(
            CoreApiProperties properties) {
        FilterRegistrationBean<CoreApiCredentialFilter> registration =
                new FilterRegistrationBean<>(new CoreApiCredentialFilter(properties));
        registration.addUrlPatterns(CoreApiCredentialFilter.API_URL_PATTERN);
        // 기본값은 REQUEST뿐이다. FORWARD·INCLUDE·ASYNC·ERROR로 같은 경로에 다시 들어오는 길을
        // 열어두면 필터를 우회할 수 있으므로 전부 명시한다.
        registration.setDispatcherTypes(
                EnumSet.of(
                        DispatcherType.REQUEST,
                        DispatcherType.FORWARD,
                        DispatcherType.INCLUDE,
                        DispatcherType.ASYNC,
                        DispatcherType.ERROR));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }
}
