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
import com.github.tomakehurst.wiremock.http.Fault;

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
        // 운영 기본값 300ms는 connect와 read 양쪽에 걸린다. 이 테스트의 첫 왕복은 클래스 로딩과
        // JIT, WireMock 초기화를 함께 치르느라 그 예산을 넘는다 — CI에서 1122ms가 측정됐고
        // 그때 OPA가 stub을 정상 응답했는데도(serveEvents=1, unmatched=0) 클라이언트가 먼저
        // 포기해 fail-closed 403 POLICY_ENGINE_UNAVAILABLE이 나왔다.
        //
        // 이 테스트가 검증하는 것은 BLOCK 판정이 호출자에게 그대로 전달되는지이지 타임아웃 예산이
        // 아니다. fail-closed 경로는 opaDownTriggersFailClosedBlock이 따로 검증한다.
        // 운영 값(300ms)이 콜드 스타트에 충분한지는 별도로 측정할 일이다.
        registry.add("finguard.timeouts.opa-ms", () -> "5000");
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

    /**
     * OPA에 닿지 못하는 상황을 서버를 내려서 만들지 않는다. {@code dynamicPort()}로 띄운 WireMock은
     * {@code stop()} 후 {@code start()} 하면 다른 포트에 붙는데, {@link io.finguard.gateway.client.OpaClient}는
     * 생성 시점의 URL을 필드에 들고 있어 되살린 서버를 다시 찾지 못한다. 그러면 이 뒤에 실행되는
     * 모든 테스트가 실제 검증 대신 fail-closed 응답을 받는다. 연결 자체를 끊는 stub으로 같은 경로를 만든다.
     */
    @Test
    void opaDownTriggersFailClosedBlock() throws Exception {
        opaMock.stubFor(WireMock.post(WireMock.urlEqualTo("/v1/data/finguard/authorization/decision"))
            .willReturn(WireMock.aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

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
    }
}
