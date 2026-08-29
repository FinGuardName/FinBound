package io.finguard.gateway.client;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import io.finguard.gateway.authorization.AuthorizationContext;
import io.finguard.gateway.authorization.PolicyDecisionResult;
import io.finguard.gateway.exception.OpaUnavailableException;

@Component
public class OpaClient {

    private static final String DECISION_PATH = "/v1/data/finguard/authorization/decision";

    private final RestClient restClient;
    private final String opaUrl;

    public OpaClient(RestClient.Builder builder,
                     @Value("${finguard.opa.base-url}") String opaUrl) {
        this.restClient = builder.build();
        this.opaUrl = opaUrl;
    }

    public PolicyDecisionResult decide(AuthorizationContext context) {
        try {
            OpaResponse response = restClient.post()
                .uri(opaUrl + DECISION_PATH)
                .body(Map.of("input", context))
                .retrieve()
                .body(OpaResponse.class);
            if (response == null || response.result() == null) {
                throw new OpaUnavailableException("OPA returned empty response");
            }
            return response.result();
        } catch (RestClientException e) {
            throw new OpaUnavailableException("OPA call failed", e);
        }
    }

    public record OpaResponse(PolicyDecisionResult result) {
    }
}
