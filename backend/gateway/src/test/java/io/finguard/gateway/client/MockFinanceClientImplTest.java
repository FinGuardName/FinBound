package io.finguard.gateway.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import io.finguard.gateway.client.impl.MockFinanceClientImpl;
import io.finguard.gateway.contract.FinancialAction;
import io.finguard.gateway.contract.FinancialDataType;
import io.finguard.gateway.contract.FinancialTool;
import io.finguard.gateway.dto.DownstreamToolResult;
import io.finguard.gateway.dto.ToolCallRequest;
import io.finguard.gateway.exception.DownstreamTimeoutException;
import io.finguard.gateway.exception.DownstreamUnavailableException;

class MockFinanceClientImplTest {

    private WireMockServer server;
    private MockFinanceClientImpl client;

    private final ToolCallRequest request = new ToolCallRequest(
        "RUN-001", "PASS-001", FinancialTool.CREDIT_SCORE_READ, "CUST-1001",
        List.of(FinancialDataType.CREDIT_SCORE), FinancialAction.READ);

    @BeforeEach
    void setUp() {
        server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
        client = new MockFinanceClientImpl(server.baseUrl(), "internal-secret", 1_000);
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void executeCallsMockFinanceWithInternalCredential() {
        server.stubFor(post(urlEqualTo("/internal/v1/finance/tool-calls"))
            .withHeader("X-FinGuard-Internal-Credential", equalTo("internal-secret"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                      "requestId": "REQ-1",
                      "tool": "CREDIT_SCORE_READ",
                      "consumerId": "CUST-1001",
                      "result": {"creditScore": 812}
                    }
                    """)));

        DownstreamToolResult result = client.execute(request, "REQ-1", "trace");

        assertThat(result.result()).containsEntry("creditScore", 812);
    }

    @Test
    void serverFailureIsMappedToDownstreamUnavailable() {
        server.stubFor(post(urlEqualTo("/internal/v1/finance/tool-calls"))
            .willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(() -> client.execute(request, "REQ-1", "trace"))
            .isInstanceOf(DownstreamUnavailableException.class);
    }

    @Test
    void timeoutIsMappedToDownstreamTimeout() {
        server.stubFor(post(urlEqualTo("/internal/v1/finance/tool-calls"))
            .willReturn(aResponse()
                .withFixedDelay(500)
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                      "requestId": "REQ-1",
                      "tool": "CREDIT_SCORE_READ",
                      "consumerId": "CUST-1001",
                      "result": {"creditScore": 812}
                    }
                    """)));
        MockFinanceClientImpl shortTimeoutClient =
            new MockFinanceClientImpl(server.baseUrl(), "internal-secret", 50);

        assertThatThrownBy(() -> shortTimeoutClient.execute(request, "REQ-1", "trace"))
            .isInstanceOf(DownstreamTimeoutException.class);
    }
}
