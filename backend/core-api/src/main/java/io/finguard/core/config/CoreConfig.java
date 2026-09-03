package io.finguard.core.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.finguard.core.context.FinancialContextResolver;
import io.finguard.core.permission.EffectivePermissionCalculator;

/**
 * 도메인 협력자 배선.
 *
 * <p>{@link EffectivePermissionCalculator}는 순수 도메인 클래스다. 여기서 빈으로 만들어 도메인이 Spring을
 * 알지 못하게 둔다 — 그래야 컨테이너 없이 단위 테스트할 수 있다.
 *
 * <p>{@link Clock}을 주입 가능하게 두는 이유는 만료·시각 판정이 도처에 있기 때문이다.
 * {@code Instant.now()}를 코드 안에서 직접 부르면 그 분기를 테스트할 방법이 없다.
 */
@Configuration
public class CoreConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    EffectivePermissionCalculator effectivePermissionCalculator() {
        return new EffectivePermissionCalculator();
    }

    @Bean
    FinancialContextResolver financialContextResolver() {
        return new FinancialContextResolver();
    }

}
