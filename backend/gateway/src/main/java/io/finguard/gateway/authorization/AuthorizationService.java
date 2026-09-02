package io.finguard.gateway.authorization;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import io.finguard.gateway.client.AiClient;
import io.finguard.gateway.client.CoreClient;
import io.finguard.gateway.client.OpaClient;
import io.finguard.gateway.dto.BehaviorRiskInput;
import io.finguard.gateway.dto.HardLimits;
import io.finguard.gateway.dto.ResolvedContext;
import io.finguard.gateway.dto.RiskInput;
import io.finguard.gateway.dto.ToolCallRequest;
import io.finguard.gateway.exception.AiUnavailableException;
import io.finguard.gateway.exception.CoreUnavailableException;
import io.finguard.gateway.exception.OpaUnavailableException;
import io.finguard.gateway.identity.VerifiedAgentIdentity;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthorizationService {

    private final CoreClient coreClient;
    private final AiClient aiClient;
    private final OpaClient opaClient;

    public PolicyDecisionResult decide(VerifiedAgentIdentity identity,
                                       ToolCallRequest request,
                                       String requestId) {
        try {
            ResolvedContext resolved = coreClient.resolveContext(identity, request, requestId);
            if (!"EVALUATED".equals(resolved.promptRisk().evaluationStatus())) {
                log.warn("PROMPT_RISK_UNAVAILABLE requestId={}", requestId);
                return PolicyDecisionResult.block("PROMPT_RISK_UNAVAILABLE");
            }
            BehaviorRiskInput behavior = aiClient.evaluate(identity, request, requestId);
            RiskInput risk = RiskInput.combine(resolved.promptRisk(), behavior);
            AuthorizationContext context =
                new AuthorizationContext(resolved.scopeStatus(), risk, new HardLimits(false));
            return opaClient.decide(context);
        } catch (CoreUnavailableException e) {
            return failClosed("CONTEXT_SERVICE_UNAVAILABLE", requestId, e);
        } catch (AiUnavailableException e) {
            return failClosed("BEHAVIOR_RISK_UNAVAILABLE", requestId, e);
        } catch (OpaUnavailableException e) {
            return failClosed("POLICY_ENGINE_UNAVAILABLE", requestId, e);
        }
    }

    private PolicyDecisionResult failClosed(String reasonCode, String requestId, Exception cause) {
        log.warn("{} requestId={}", reasonCode, requestId, cause);
        return PolicyDecisionResult.block(reasonCode);
    }
}
