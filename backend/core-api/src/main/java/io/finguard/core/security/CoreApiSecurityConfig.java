package io.finguard.core.security;

import java.util.EnumSet;
import java.util.List;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import jakarta.servlet.DispatcherType;

/** 브라우저(Vue) → Core {@code /api/v1/**} 인증·인가 배선. {@code docs/04-api-contract.md} §2. */
@Configuration
@EnableConfigurationProperties(CoreApiProperties.class)
public class CoreApiSecurityConfig implements WebMvcConfigurer {

    @Bean
    FilterRegistrationBean<CoreApiCredentialFilter> coreApiCredentialFilterRegistration(
            CoreApiProperties properties) {
        FilterRegistrationBean<CoreApiCredentialFilter> registration =
                new FilterRegistrationBean<>(new CoreApiCredentialFilter(properties));
        registration.addUrlPatterns(CoreApiCredentialFilter.API_URL_PATTERN);
        // 기본값은 REQUEST뿐이라 FORWARD·INCLUDE로 같은 경로에 다시 들어오는 길을 함께 등록한다.
        //
        // ASYNC·ERROR도 등록하지만 여기 적는 것만으로 검사가 도는 것은 아니다.
        // OncePerRequestFilter 는 shouldNotFilterAsyncDispatch()·shouldNotFilterErrorDispatch() 가
        // 둘 다 true라 그 두 dispatch에서는 스스로 비켜선다. 실제 구멍은 아니다 — ASYNC는 이미 검사를
        // 통과한 요청의 연속이고 ERROR는 컨테이너 내부에서만 시작된다. 등록을 남겨 두는 것은 나중에
        // shouldNotFilter* 를 뒤집기로 할 때 여기까지 함께 고치지 않아도 되게 하기 위해서다.
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

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 경로를 지정하지 않는다. 판정은 필터가 심은 Principal의 유무로 한다 —
        // 경로 문자열이 두 곳에 생기면 그 둘이 갈리는 순간이 우회가 된다.
        registry.addInterceptor(new CoreApiRoleInterceptor());
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CoreApiPrincipalArgumentResolver());
    }

    /**
     * 권한을 밝히지 않은 {@code /api/v1} 핸들러가 있으면 기동을 멈춘다.
     *
     * <p>{@code SmartInitializingSingleton}인 이유는 시점 때문이다. 이 시점에는 HandlerMapping이 매핑을
     * 마쳤고 웹 서버는 아직 요청을 받지 않는다. 즉 <strong>열린 채로 떠 있는 순간이 없다.</strong>
     */
    @Bean
    SmartInitializingSingleton apiRoleDeclarationCheck(ApplicationContext context) {
        return () ->
                ApiRoleDeclarations.verifyEveryApiHandlerDeclaresRoles(
                        context.getBean(
                                        "requestMappingHandlerMapping", RequestMappingHandlerMapping.class)
                                .getHandlerMethods());
    }
}
