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
import org.springframework.web.client.RestClientResponseException;

/**
 * {@code POST /internal/v1/risk/prompt} 어댑터. {@code docs/04-api-contract.md} §8.
 *
 * <p>타임아웃을 반드시 건다. 걸지 않으면 ai-risk 가 응답하지 않을 때 AgentRun 생성이 통째로 매달린다.
 */
@Component
@EnableConfigurationProperties(PromptRiskProperties.class)
public class PromptRiskHttpClient implements PromptRiskClient {

    private static final Logger log = LoggerFactory.getLogger(PromptRiskHttpClient.class);

    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String TRACEPARENT_HEADER = "Traceparent";

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
            String agentRunId, String inputRef, String inputText, String inputHash, RequestTrace trace) {
        try {
            PromptRiskEvaluation.Raw raw =
                    restClient
                            .post()
                            .uri("/internal/v1/risk/prompt")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(REQUEST_ID_HEADER, trace.requestId())
                            .headers(
                                    headers -> {
                                        if (trace.traceparent() != null) {
                                            headers.set(TRACEPARENT_HEADER, trace.traceparent());
                                        }
                                    })
                            .body(new PromptRiskRequest(agentRunId, inputRef, inputText, inputHash))
                            .retrieve()
                            .body(PromptRiskEvaluation.Raw.class);
            return PromptRiskEvaluation.from(raw, inputHash);
        } catch (RestClientResponseException httpFailure) {
            // 예외 객체를 그대로 넘기지 않는다. Spring 은 4xx/5xx 메시지에 응답 본문을 담고,
            // ai-risk 가 검증 실패로 입력을 되비추면 원문이 로그에 남는다 — docs/06 §24 위반.
            // 상태 코드만 적는다.
            log.warn(
                    "Prompt risk evaluation returned {}. agentRunId={}",
                    httpFailure.getStatusCode().value(),
                    agentRunId);
            return Optional.empty();
        } catch (RuntimeException failure) {
            // 연결 실패·타임아웃. 메시지에 호스트가 들어갈 수 있어 예외 종류만 적는다.
            log.warn(
                    "Prompt risk evaluation is unavailable ({}). agentRunId={}",
                    failure.getClass().getSimpleName(),
                    agentRunId);
            return Optional.empty();
        }
    }

    /**
     * {@code contentLanguage} 는 보내지 않는다. Core 가 판별하지 않았으므로 지어내지 않는다 — §8.
     *
     * <p>{@code toString} 을 덮어쓴다. record 의 기본 구현은 {@code inputText} 를 그대로 찍는데,
     * Spring 의 RestClient DEBUG 로깅이 본문에 {@code toString} 을 부른다 — 원문은 해시만 남긴다(§24).
     */
    record PromptRiskRequest(
            String agentRunId, String inputRef, String inputText, String inputHash) {

        @Override
        public String toString() {
            return "PromptRiskRequest[agentRunId=%s, inputRef=%s, inputHash=%s, inputText=(redacted)]"
                    .formatted(agentRunId, inputRef, inputHash);
        }
    }
}
