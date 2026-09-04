package io.finguard.core.risk;

import java.time.Duration;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * {@code POST /internal/v1/risk/prompt} 어댑터. {@code docs/04-api-contract.md} §8.
 *
 * <p>타임아웃을 반드시 건다. 걸지 않으면 ai-risk 가 응답하지 않을 때 AgentRun 생성이 통째로 매달린다.
 */
@Component
@EnableConfigurationProperties(PromptRiskProperties.class)
public class PromptRiskHttpClient implements PromptRiskClient {

    private static final Logger log = LoggerFactory.getLogger(PromptRiskHttpClient.class);

    private final RestClient restClient;

    public PromptRiskHttpClient(RestClient.Builder builder, PromptRiskProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(properties.connectTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(properties.readTimeoutMs()));

        this.restClient =
                builder.baseUrl(properties.baseUrl())
                        .requestFactory(factory)
                        .defaultHeader(
                                PromptRiskProperties.SERVICE_CREDENTIAL_HEADER,
                                properties.serviceCredential())
                        .build();
    }

    @Override
    public Optional<PromptRiskEvaluation> evaluate(
            String agentRunId, String inputRef, String inputText, String inputHash) {
        try {
            PromptRiskEvaluation.Raw raw =
                    restClient
                            .post()
                            .uri("/internal/v1/risk/prompt")
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(new PromptRiskRequest(agentRunId, inputRef, inputText, inputHash))
                            .retrieve()
                            .body(PromptRiskEvaluation.Raw.class);
            return PromptRiskEvaluation.from(raw, inputHash);
        } catch (RuntimeException failure) {
            // 원문도 응답 본문도 남기지 않는다 — docs/06 §24·§26. 식별자만 적는다.
            log.warn("Prompt risk evaluation is unavailable. agentRunId={}", agentRunId, failure);
            return Optional.empty();
        }
    }

    /** {@code contentLanguage} 는 보내지 않는다. Core 가 판별하지 않았으므로 지어내지 않는다 — §8. */
    private record PromptRiskRequest(
            String agentRunId, String inputRef, String inputText, String inputHash) {
    }
}
