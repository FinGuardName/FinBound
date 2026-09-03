package io.finguard.gateway.client.impl;

import java.net.http.HttpClient;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import io.finguard.gateway.client.AiClient;
import io.finguard.gateway.dto.BehaviorHistory;
import io.finguard.gateway.dto.BehaviorRiskResult;
import io.finguard.gateway.dto.ResolvedContext;
import io.finguard.gateway.dto.ToolCallRequest;
import io.finguard.gateway.exception.AiUnavailableException;
import io.finguard.gateway.identity.VerifiedAgentIdentity;

@Component
@Profile("real-ai")
public class AiClientImpl implements AiClient {

    private static final String SERVICE_CREDENTIAL_HEADER = "X-FinGuard-Service-Credential";
    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String TRACEPARENT_HEADER = "Traceparent";

    private final RestClient restClient;
    private final String baseUrl;
    private final String internalCredential;

    public AiClientImpl(@Value("${finguard.ai.base-url}") String baseUrl,
                        @Value("${finguard.credentials.internal-service}") String internalCredential,
                        @Value("${finguard.timeouts.ai-ms}") long timeoutMs) {
        this.restClient = RestClient.builder().requestFactory(requestFactory(timeoutMs)).build();
        this.baseUrl = baseUrl;
        this.internalCredential = internalCredential;
    }

    @Override
    public BehaviorRiskResult evaluateBehavior(VerifiedAgentIdentity identity,
                                               ToolCallRequest request,
                                               ResolvedContext context,
                                               BehaviorHistory history,
                                               String requestId,
                                               String traceparent,
                                               Instant requestedAt) {
        try {
            BehaviorRiskResult response = restClient.post()
                .uri(baseUrl + "/internal/v1/risk/behavior")
                .headers(headers -> {
                    headers.set(SERVICE_CREDENTIAL_HEADER, internalCredential);
                    headers.set(REQUEST_ID_HEADER, requestId);
                    if (traceparent != null && !traceparent.isBlank()) {
                        headers.set(TRACEPARENT_HEADER, traceparent);
                    }
                })
                .body(Map.of(
                    "requestId", requestId,
                    "agentId", identity.agentId(),
                    "agentRunId", request.agentRunId(),
                    "history", completedEvents(history),
                    "currentAttempt", Map.of(
                        "caseId", context.references().caseId(),
                        "targetConsumerId", request.targetConsumerId(),
                        "tool", request.tool(),
                        "requestedData", request.requestedData(),
                        "requestedAt", requestedAt)))
                .retrieve()
                .body(BehaviorRiskResult.class);
            if (response == null || response.behaviorRiskLevel() == null) {
                throw new AiUnavailableException("AI behavior response is incomplete");
            }
            return response;
        } catch (RestClientException e) {
            throw new AiUnavailableException("AI behavior API call failed", e);
        }
    }

    private List<Map<String, Object>> completedEvents(BehaviorHistory history) {
        return history.completedEvents().stream()
            .filter(this::isCompleteEvent)
            .map(this::completedEventBody)
            .toList();
    }

    private boolean isCompleteEvent(BehaviorHistory.CompletedEvent event) {
        return event.requestId() != null
            && event.caseId() != null
            && event.targetConsumerId() != null
            && event.tool() != null
            && event.requestedAt() != null
            && event.decision() != null;
    }

    private Map<String, Object> completedEventBody(BehaviorHistory.CompletedEvent event) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("requestId", event.requestId());
        body.put("caseId", event.caseId());
        body.put("targetConsumerId", event.targetConsumerId());
        body.put("tool", event.tool());
        body.put("requestedAt", event.requestedAt());
        body.put("decision", event.decision());
        body.put("success", Boolean.TRUE.equals(event.success()));
        body.put("latencyMs", event.latencyMs() == null ? 0L : event.latencyMs());
        body.put("requestedData", event.requestedData());
        return body;
    }

    private JdkClientHttpRequestFactory requestFactory(long timeoutMs) {
        HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(java.time.Duration.ofMillis(timeoutMs))
            .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(java.time.Duration.ofMillis(timeoutMs));
        return factory;
    }
}
