package io.finguard.gateway.config;

import java.time.Clock;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class ClientsConfig {

    @Bean
    RestClient.Builder restClientBuilder(@Value("${finguard.timeouts.opa-ms}") long opaTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(opaTimeoutMs));
        factory.setReadTimeout(Duration.ofMillis(opaTimeoutMs));
        return RestClient.builder().requestFactory(factory);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
