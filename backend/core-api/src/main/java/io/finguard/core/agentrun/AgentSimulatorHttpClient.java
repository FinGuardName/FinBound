package io.finguard.core.agentrun;

import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.util.Set;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import io.finguard.core.security.InternalApiProperties;

@Component
public class AgentSimulatorHttpClient implements AgentSimulatorClient {
    static final String INTERNAL_CREDENTIAL_HEADER = "X-FinGuard-Internal-Credential";
    private static final String SIMULATIONS_PATH = "/internal/v1/agent-simulations";
    private static final String NORMAL_SCENARIO = "NORMAL_CREDIT_SCORE";
    private static final Set<String> SUPPORTED_DECISIONS = Set.of("ALLOW", "BLOCK");

    private final RestClient restClient;
    private final String internalCredential;

    public AgentSimulatorHttpClient(
            RestClient.Builder restClientBuilder,
            AgentSimulatorProperties properties,
            InternalApiProperties internalApiProperties
    ) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(properties.timeout()).build());
        requestFactory.setReadTimeout(properties.timeout());
        this.restClient = restClientBuilder
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
        this.internalCredential = internalApiProperties.credential();
    }

    AgentSimulatorHttpClient(RestClient restClient, String internalCredential) {
        this.restClient = restClient;
        this.internalCredential = internalCredential;
    }

    @Override
    public void simulate(String agentRunId, String passportId) {
        try {
            AgentSimulationResponse response = restClient.post()
                    .uri(SIMULATIONS_PATH)
                    .header(INTERNAL_CREDENTIAL_HEADER, internalCredential)
                    .body(new AgentSimulationRequest(agentRunId, passportId, NORMAL_SCENARIO))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, result) -> {
                        throw new AgentSimulatorCallException("AGENT_SIMULATOR_UNAVAILABLE");
                    })
                    .body(AgentSimulationResponse.class);
            if (response == null
                    || !NORMAL_SCENARIO.equals(response.scenario())
                    || response.gatewayResponse() == null
                    || !SUPPORTED_DECISIONS.contains(response.gatewayResponse().decision())) {
                throw new AgentSimulatorCallException("AGENT_SIMULATOR_RESPONSE_INVALID");
            }
        } catch (AgentSimulatorCallException exception) {
            throw exception;
        } catch (ResourceAccessException exception) {
            String errorCode = causedByTimeout(exception)
                    ? "AGENT_SIMULATOR_TIMEOUT"
                    : "AGENT_SIMULATOR_UNAVAILABLE";
            throw new AgentSimulatorCallException(errorCode, exception);
        } catch (RestClientException exception) {
            throw new AgentSimulatorCallException("AGENT_SIMULATOR_UNAVAILABLE", exception);
        }
    }

    private boolean causedByTimeout(Throwable exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof SocketTimeoutException || cause instanceof HttpTimeoutException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private record AgentSimulationRequest(
            String agentRunId,
            String passportId,
            String scenario
    ) {
    }

    private record AgentSimulationResponse(
            String scenario,
            GatewayResponse gatewayResponse
    ) {
    }

    private record GatewayResponse(String decision) {
    }
}
