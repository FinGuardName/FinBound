package io.finguard.core.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/** Core Internal API의 서비스 간 인증 배선. */
@Configuration
@EnableConfigurationProperties(InternalApiProperties.class)
public class InternalApiSecurityConfig {

    @Bean
    FilterRegistrationBean<InternalCredentialFilter> internalCredentialFilterRegistration(
            InternalApiProperties properties) {
        FilterRegistrationBean<InternalCredentialFilter> registration =
                new FilterRegistrationBean<>(new InternalCredentialFilter(properties.credential()));
        registration.addUrlPatterns(InternalCredentialFilter.INTERNAL_URL_PATTERN);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }
}
