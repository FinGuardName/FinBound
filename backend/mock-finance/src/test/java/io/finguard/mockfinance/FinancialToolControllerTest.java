package io.finguard.mockfinance;

import static io.finguard.mockfinance.security.InternalCredentialFilter.INTERNAL_CREDENTIAL_HEADER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import io.finguard.mockfinance.domain.FinancialTool;
import io.finguard.mockfinance.service.ToolInvocationCounter;

@SpringBootTest(properties = "finguard.mock-finance.internal-credential=test-internal-credential")
@AutoConfigureMockMvc
class FinancialToolControllerTest {
    private static final String ENDPOINT = "/internal/v1/finance/tool-calls";
    private static final String VALID_CREDENTIAL = "test-internal-credential";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ToolInvocationCounter invocationCounter;

    @BeforeEach
    void resetCounter() {
        invocationCounter.reset();
    }

    @ParameterizedTest
    @MethodSource("supportedToolRequests")
    void returnsMockFinancialData(
            FinancialTool tool,
            String resultField,
            long expectedValue
    ) throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .header(INTERNAL_CREDENTIAL_HEADER, VALID_CREDENTIAL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(tool.name(), "CUST-1001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value("REQ-001"))
                .andExpect(jsonPath("$.tool").value(tool.name()))
                .andExpect(jsonPath("$.consumerId").value("CUST-1001"))
                .andExpect(jsonPath("$.result." + resultField).value(expectedValue));

        assertThat(invocationCounter.count(tool)).isEqualTo(1);
    }

    @Test
    void rejectsMissingInternalCredentialBeforeToolExecution() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(FinancialTool.CREDIT_SCORE_READ.name(), "CUST-1001")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_CREDENTIAL_INVALID"));

        assertThat(invocationCounter.count(FinancialTool.CREDIT_SCORE_READ)).isZero();
    }

    @Test
    void rejectsInvalidInternalCredentialBeforeToolExecution() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .header(INTERNAL_CREDENTIAL_HEADER, "wrong-credential")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(FinancialTool.CREDIT_SCORE_READ.name(), "CUST-1001")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_CREDENTIAL_INVALID"));

        assertThat(invocationCounter.count(FinancialTool.CREDIT_SCORE_READ)).isZero();
    }

    @Test
    void rejectsUnsupportedToolBeforeToolExecution() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .header(INTERNAL_CREDENTIAL_HEADER, VALID_CREDENTIAL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson("ACCOUNT_BALANCE_READ", "CUST-1001")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOOL_REQUEST"));

        for (FinancialTool tool : FinancialTool.values()) {
            assertThat(invocationCounter.count(tool)).isZero();
        }
    }

    @Test
    void returnsNotFoundAfterDownstreamInvocationForUnknownConsumer() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .header(INTERNAL_CREDENTIAL_HEADER, VALID_CREDENTIAL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(FinancialTool.DEBT_READ.name(), "CUST-4040")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("FINANCIAL_DATA_NOT_FOUND"));

        assertThat(invocationCounter.count(FinancialTool.DEBT_READ)).isEqualTo(1);
    }

    @Test
    void allowsHealthCheckWithoutInternalCredential() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    private static Stream<Arguments> supportedToolRequests() {
        return Stream.of(
                Arguments.of(FinancialTool.CREDIT_SCORE_READ, "creditScore", 812L),
                Arguments.of(FinancialTool.INCOME_READ, "annualIncome", 85_000_000L),
                Arguments.of(FinancialTool.DEBT_READ, "totalDebt", 25_000_000L)
        );
    }

    private static String requestJson(String tool, String consumerId) {
        return """
                {
                  "requestId": "REQ-001",
                  "tool": "%s",
                  "targetConsumerId": "%s"
                }
                """.formatted(tool, consumerId);
    }
}
