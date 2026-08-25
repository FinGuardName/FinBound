package io.finguard.gateway;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

/**
 * Phase 1 회의 데모 시나리오 3개:
 *  - 정상 인증 + customerScope=VIOLATION → 403 CASE_SCOPE_VIOLATION
 *  - 인증 헤더 없음 → 401
 *  - OPA 다운 → Fail-closed 403 POLICY_ENGINE_UNAVAILABLE
 */
@SpringBootTest
class ToolCallIntegrationTest {

    private static WireMockServer opaMock;

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @BeforeAll
    static void startOpa() {
        opaMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        opaMock.start();
    }

    @AfterAll
    static void stopOpa() {
        opaMock.stop();
    }

    @DynamicPropertySource
    static void overrideOpaUrl(DynamicPropertyRegistry registry) {
        registry.add("finguard.opa.base-url", () -> "http://localhost:" + opaMock.port());
    }

    @BeforeEach
    void setUp() {
        opaMock.resetAll();
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void violationScopeIsBlockedByOpa() throws Exception {
        opaMock.stubFor(WireMock.post(WireMock.urlEqualTo("/v1/data/finguard/authorization/decision"))
            .willReturn(WireMock.aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                      "result": {
                        "decision": "BLOCK",
                        "severity": "CRITICAL",
                        "riskFlagged": true,
                        "reasonCodes": ["CASE_SCOPE_VIOLATION"],
                        "policyVersion": "loan-review-policy-1"
                      }
                    }
                    """)));

        mockMvc.perform(post("/gateway/v1/tool-calls")
                .header("Authorization", "Bearer valid-agent-token")
                .header("X-Request-Id", "DEMO-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "agentRunId": "RUN-001",
                      "passportId": "PASS-001",
                      "tool": "CREDIT_SCORE_READ",
                      "targetConsumerId": "CUST-9999",
                      "requestedData": ["CREDIT_SCORE"],
                      "action": "READ"
                    }
                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.decision").value("BLOCK"))
            .andExpect(jsonPath("$.reasonCodes[0]").value("CASE_SCOPE_VIOLATION"));
    }

    @Test
    void missingCredentialReturns401() throws Exception {
        mockMvc.perform(post("/gateway/v1/tool-calls")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "agentRunId": "RUN-001",
                      "passportId": "PASS-001",
                      "tool": "CREDIT_SCORE_READ",
                      "targetConsumerId": "CUST-1001",
                      "requestedData": ["CREDIT_SCORE"],
                      "action": "READ"
                    }
                    """))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void opaDownTriggersFailClosedBlock() throws Exception {
        opaMock.stop();
        try {
            mockMvc.perform(post("/gateway/v1/tool-calls")
                    .header("Authorization", "Bearer valid-agent-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "agentRunId": "RUN-001",
                          "passportId": "PASS-001",
                          "tool": "CREDIT_SCORE_READ",
                          "targetConsumerId": "CUST-1001",
                          "requestedData": ["CREDIT_SCORE"],
                          "action": "READ"
                        }
                        """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.decision").value("BLOCK"))
                .andExpect(jsonPath("$.reasonCodes[0]").value("POLICY_ENGINE_UNAVAILABLE"));
        } finally {
            opaMock.start();
        }
    }
}
