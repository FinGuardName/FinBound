package io.finguard.core.agentrun;

import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import io.finguard.core.domain.AgentSimulationScenario;

/**
 * {@code POST /internal/v1/agent-simulations} 어댑터. {@code docs/04-api-contract.md} §3.1.
 *
 * <p><strong>Core가 내부 자격 증명을 나가는 데 쓰는 첫 자리다.</strong> 지금까지는 들어오는 요청을
 * 검증하는 데만 썼다. Agent의 {@code /internal/**}도 같은 공유 자격 증명으로 보호되므로 같은 값을
 * 보낸다 — 값이 하나뿐인 것은 P0의 전제다({@code docs/04} §1).
 *
 * <p>타임아웃을 반드시 건다. 걸지 않으면 Agent가 응답하지 않을 때 실행이 영원히 {@code RUNNING}으로
 * 남고, 그건 이 작업이 없애려던 상태다.
 */
@Component
@EnableConfigurationProperties(AgentSimulationProperties.class)
public class AgentSimulationHttpClient implements AgentSimulationClient {

    private final RestClient restClient;

    public AgentSimulationHttpClient(
            RestClient.Builder builder, AgentSimulationProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(properties.connectTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(properties.readTimeoutMs()));

        this.restClient =
                builder.baseUrl(properties.baseUrl())
                        .requestFactory(factory)
                        .defaultHeader(
                                AgentSimulationProperties.INTERNAL_CREDENTIAL_HEADER,
                                properties.internalCredential())
                        .build();
    }

    @Override
    public void simulate(String agentRunId, String passportId, AgentSimulationScenario scenario) {
        try {
            restClient
                    .post()
                    .uri("/internal/v1/agent-simulations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new SimulationRequest(agentRunId, passportId, scenario))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException failure) {
            // 응답 본문을 예외 메시지에 담지 않는다 — docs/06 §26.
            throw new AgentSimulationFailedException(agentRunId, failure);
        }
    }

    private record SimulationRequest(
            String agentRunId, String passportId, AgentSimulationScenario scenario) {
    }
}
