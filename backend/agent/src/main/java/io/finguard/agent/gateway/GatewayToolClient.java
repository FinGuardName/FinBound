package io.finguard.agent.gateway;

import java.util.UUID;
import java.util.concurrent.TimeoutException;

import org.springframework.core.codec.DecodingException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import io.finguard.agent.config.AgentProperties;
import io.finguard.agent.domain.PolicyDecision;
import reactor.core.publisher.Mono;

@Component
public class GatewayToolClient {
    static final String REQUEST_ID_HEADER = "X-Request-Id";
    static final String TRACEPARENT_HEADER = "Traceparent";

    private final WebClient webClient;
    private final AgentProperties properties;

    public GatewayToolClient(WebClient.Builder webClientBuilder, AgentProperties properties) {
        this.webClient = webClientBuilder.baseUrl(properties.gatewayBaseUrl()).build();
        this.properties = properties;
    }

    public Mono<GatewayToolCallResponse> execute(GatewayToolCallRequest request) {
        return webClient.post()
                .uri("/gateway/v1/tool-calls")
                .headers(headers -> addRuntimeHeaders(headers, properties.serviceCredential()))
                .bodyValue(request)
                .exchangeToMono(response -> {
                    if (response.statusCode().is2xxSuccessful()
                            || response.statusCode().value() == HttpStatus.FORBIDDEN.value()) {
                        return response.bodyToMono(GatewayToolCallResponse.class)
                                .switchIfEmpty(Mono.error(new GatewayCallException(
                                        "GATEWAY_RESPONSE_INVALID"
                                )))
                                .flatMap(body -> validateResponse(
                                        body, response.statusCode().value()));
                    }
                    return response.releaseBody().then(Mono.error(
                            new GatewayCallException("GATEWAY_REQUEST_FAILED")
                    ));
                })
                .timeout(properties.gatewayTimeout())
                .onErrorMap(DecodingException.class, exception ->
                        new GatewayCallException("GATEWAY_RESPONSE_INVALID"))
                .onErrorMap(TimeoutException.class, exception ->
                        new GatewayCallException("GATEWAY_TIMEOUT"))
                .onErrorMap(
                        exception -> !(exception instanceof GatewayCallException),
                        exception -> new GatewayCallException("GATEWAY_UNAVAILABLE")
                );
    }

    private Mono<GatewayToolCallResponse> validateResponse(
            GatewayToolCallResponse response, int status) {
        if (response.requestId() == null || response.requestId().isBlank()
                || response.decision() == null) {
            return Mono.error(new GatewayCallException("GATEWAY_RESPONSE_INVALID"));
        }
        if (response.decision() == PolicyDecision.ALLOW
                && (status == HttpStatus.FORBIDDEN.value()
                || response.result() == null || !response.result().isObject()
                || response.result().isEmpty())) {
            return Mono.error(new GatewayCallException("GATEWAY_RESPONSE_INVALID"));
        }
        if (response.decision() == PolicyDecision.BLOCK
                && (response.reasonCodes().isEmpty()
                || response.reasonCodes().stream().anyMatch(String::isBlank)
                || (response.result() != null && !response.result().isNull()))) {
            return Mono.error(new GatewayCallException("GATEWAY_RESPONSE_INVALID"));
        }
        return Mono.just(response);
    }

    private void addRuntimeHeaders(HttpHeaders headers, String serviceCredential) {
        headers.setBearerAuth(serviceCredential);
        headers.set(REQUEST_ID_HEADER, UUID.randomUUID().toString());
        headers.set(TRACEPARENT_HEADER, createTraceparent());
    }

    private String createTraceparent() {
        String traceId = UUID.randomUUID().toString().replace("-", "");
        String spanId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        return "00-" + traceId + "-" + spanId + "-01";
    }
}
