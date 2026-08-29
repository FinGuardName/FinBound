package io.finguard.agent.agentrun.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.reactive.function.client.WebClient;

import io.finguard.agent.agentrun.port.CoreAgentRunClient;

/**
 * {@link CoreAgentRunHttpClient}는 생성자가 둘입니다. 하나는 컨테이너가 쓰고, 하나는 테스트가
 * {@link WebClient}를 직접 넣으려고 씁니다. Spring은 생성자가 여럿이면 {@code @Autowired}가 붙은
 * 것을 찾고, 못 찾으면 인자 없는 기본 생성자로 후퇴하므로 어느 쪽이 주입 대상인지 명시돼 있어야 합니다.
 *
 * <p>agent 모듈의 나머지 테스트는 컨테이너를 띄우지 않아 이 결함을 보지 못합니다. 앱 전체를 기동하지
 * 않고 이 빈 하나만 올려 생성자 해결만 검증합니다.
 */
class CoreAgentRunHttpClientContextTest {
    @Test
    void containerResolvesTheConstructorThatTakesTheWebClientBuilder() {
        new ApplicationContextRunner()
                .withPropertyValues("finguard.core-api.base-url=http://localhost:8080")
                .withBean(WebClient.Builder.class, WebClient::builder)
                .withUserConfiguration(CoreAgentRunHttpClient.class)
                .run(context -> assertThat(context)
                        .hasNotFailed()
                        .hasSingleBean(CoreAgentRunClient.class));
    }
}
