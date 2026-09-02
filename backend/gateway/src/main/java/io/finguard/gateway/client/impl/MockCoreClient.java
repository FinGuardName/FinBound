package io.finguard.gateway.client.impl;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

import io.finguard.gateway.client.CoreClient;
import io.finguard.gateway.dto.PromptRiskInput;
import io.finguard.gateway.dto.ResolvedContext;
import io.finguard.gateway.dto.ScopeStatus;
import io.finguard.gateway.dto.ToolCallRequest;
import io.finguard.gateway.identity.VerifiedAgentIdentity;

@Slf4j
@Component
@Profile("!real-core")
public class MockCoreClient implements CoreClient {

    private static final String CASE_CONSUMER = "CUST-1001";

    @Override
    public ResolvedContext resolveContext(VerifiedAgentIdentity identity,
                                          ToolCallRequest request,
                                          String requestId) {
        log.debug("[mock-core] resolveContext requestId={} target={}", requestId, request.targetConsumerId());
        String customerScope = CASE_CONSUMER.equals(request.targetConsumerId()) ? "OK" : "VIOLATION";
        return new ResolvedContext(
            new ScopeStatus(
                "OK", "OK", "OK", "OK", "OK", "OK",
                customerScope, "OK", "OK"
            ),
            new PromptRiskInput("EVALUATED", 0.05, "LOW", false)
        );
    }

    @Override
    public void recordAuthFailure(String requestId, String reasonCode) {
        log.info("[mock-core] SecurityAuthEvent recorded requestId={} reason={}", requestId, reasonCode);
    }
}
