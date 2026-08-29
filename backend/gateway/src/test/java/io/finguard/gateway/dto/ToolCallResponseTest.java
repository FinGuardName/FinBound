package io.finguard.gateway.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.finguard.gateway.contract.PolicyDecision;

class ToolCallResponseTest {

    @Test
    void allowResponseCarriesResultAndNoReasonCodes() {
        ToolCallResponse response = ToolCallResponse.allow("REQ-1", Map.of("k", "v"));
        assertThat(response.decision()).isEqualTo(PolicyDecision.ALLOW);
        assertThat(response.result()).containsEntry("k", "v");
        assertThat(response.reasonCodes()).isNull();
    }

    @Test
    void blockResponseCarriesReasonCodes() {
        ToolCallResponse response = ToolCallResponse.block("REQ-2", List.of("CASE_SCOPE_VIOLATION"));
        assertThat(response.decision()).isEqualTo(PolicyDecision.BLOCK);
        assertThat(response.result()).isNull();
        assertThat(response.reasonCodes()).containsExactly("CASE_SCOPE_VIOLATION");
    }

    @Test
    void scopeStatusAllOkFactoryReturnsNineOks() {
        ScopeStatus scope = ScopeStatus.allOk();
        assertThat(scope.customerScope()).isEqualTo("OK");
        assertThat(scope.dataScope()).isEqualTo("OK");
        assertThat(scope.employeeAuthority()).isEqualTo("OK");
    }
}
