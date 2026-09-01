package io.finguard.core.agentrun;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.net.SocketTimeoutException;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AgentSimulatorHttpClientTest {
    @Test
    void sendsCoreIssuedReferencesWithTheInternalCredential() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://agent.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AgentSimulatorHttpClient client = new AgentSimulatorHttpClient(
                builder.build(), "test-internal-credential");
        server.expect(requestTo("http://agent.test/internal/v1/agent-simulations"))
                .andExpect(header(
                        AgentSimulatorHttpClient.INTERNAL_CREDENTIAL_HEADER,
                        "test-internal-credential"))
                .andExpect(content().json("""
                        {
                          "agentRunId":"RUN-CORE-001",
                          "passportId":"PASS-CORE-001",
                          "scenario":"NORMAL_CREDIT_SCORE"
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "scenario":"NORMAL_CREDIT_SCORE",
                          "gatewayResponse":{"decision":"ALLOW"}
                        }
                        """, MediaType.APPLICATION_JSON));

        client.simulate("RUN-CORE-001", "PASS-CORE-001");

        server.verify();
    }

    @Test
    void failsClosedOnAgent4xx() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://agent.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AgentSimulatorHttpClient client = new AgentSimulatorHttpClient(builder.build(), "credential");
        server.expect(requestTo("http://agent.test/internal/v1/agent-simulations"))
                .andRespond(withResourceNotFound());

        assertErrorCode(client, "AGENT_SIMULATOR_UNAVAILABLE");
    }

    @Test
    void failsClosedOnAgent5xx() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://agent.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AgentSimulatorHttpClient client = new AgentSimulatorHttpClient(builder.build(), "credential");
        server.expect(requestTo("http://agent.test/internal/v1/agent-simulations"))
                .andRespond(withServerError());

        assertErrorCode(client, "AGENT_SIMULATOR_UNAVAILABLE");
    }

    @Test
    void failsClosedOnInvalidAgentResponse() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://agent.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AgentSimulatorHttpClient client = new AgentSimulatorHttpClient(builder.build(), "credential");
        server.expect(requestTo("http://agent.test/internal/v1/agent-simulations"))
                .andRespond(withSuccess("{\"scenario\":\"NORMAL_CREDIT_SCORE\"}",
                        MediaType.APPLICATION_JSON));

        assertErrorCode(client, "AGENT_SIMULATOR_RESPONSE_INVALID");
    }

    @Test
    void distinguishesTimeoutFromOtherTransportFailures() {
        AgentSimulatorHttpClient timeoutClient = clientFailingWith(new SocketTimeoutException());
        AgentSimulatorHttpClient unavailableClient = clientFailingWith(new IOException());

        assertErrorCode(timeoutClient, "AGENT_SIMULATOR_TIMEOUT");
        assertErrorCode(unavailableClient, "AGENT_SIMULATOR_UNAVAILABLE");
    }

    private AgentSimulatorHttpClient clientFailingWith(IOException failure) {
        RestClient restClient = RestClient.builder().requestFactory((uri, method) -> {
            throw failure;
        }).build();
        return new AgentSimulatorHttpClient(restClient, "credential");
    }

    private void assertErrorCode(AgentSimulatorHttpClient client, String errorCode) {
        assertThatThrownBy(() -> client.simulate("RUN-001", "PASS-001"))
                .isInstanceOf(AgentSimulatorCallException.class)
                .extracting("errorCode")
                .isEqualTo(errorCode);
    }
}
