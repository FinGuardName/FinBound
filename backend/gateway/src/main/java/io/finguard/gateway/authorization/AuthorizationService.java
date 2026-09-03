package io.finguard.gateway.authorization;

import java.time.Instant;

import org.springframework.stereotype.Service;

import io.finguard.gateway.client.AiClient;
import io.finguard.gateway.client.CoreClient;
import io.finguard.gateway.client.OpaClient;
import io.finguard.gateway.dto.BehaviorHistory;
import io.finguard.gateway.dto.BehaviorRiskResult;
import io.finguard.gateway.dto.HardLimits;
import io.finguard.gateway.dto.PromptRiskSnapshot;
import io.finguard.gateway.dto.ResolvedContext;
import io.finguard.gateway.dto.RiskInput;
import io.finguard.gateway.dto.ToolCallRequest;
import io.finguard.gateway.enforcement.HardLimitService;
import io.finguard.gateway.exception.AiUnavailableException;
import io.finguard.gateway.exception.BehaviorHistoryUnavailableException;
import io.finguard.gateway.exception.CoreUnavailableException;
import io.finguard.gateway.exception.OpaUnavailableException;
import io.finguard.gateway.exception.PromptRiskUnavailableException;
import io.finguard.gateway.identity.VerifiedAgentIdentity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Core → AI → OPA 순차 오케스트레이션. 어느 단계든 실패하면 Fail-closed BLOCK.
 * 반환은 판정(OPA)과 관찰치(behaviorRisk)를 함께 담은 {@link AuthorizationOutcome}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthorizationService {

    private final CoreClient coreClient;
    private final AiClient aiClient;
    private final OpaClient opaClient;
    private final HardLimitService hardLimitService;

    public AuthorizationOutcome decide(VerifiedAgentIdentity identity,
                                       ToolCallRequest request,
                                       String requestId,
                                       String traceparent,
                                       Instant requestedAt) {
        try {
            ResolvedContext resolvedContext = coreClient.resolveContext(identity, request, requestId, traceparent);
            BehaviorHistory history = coreClient.behaviorHistory(identity, "5m", requestId, traceparent);
            BehaviorRiskResult behavior = aiClient.evaluateBehavior(
                identity, request, resolvedContext, history, requestId, traceparent, requestedAt);
            AuthorizationContext context = new AuthorizationContext(
                requestId,
                resolvedContext.scopeStatus(),
                riskInput(resolvedContext.promptRiskSnapshot(), behavior),
                new HardLimits(hardLimitService.isExceeded(identity.agentId())));
            PolicyDecisionResult decision = opaClient.decide(context);
            return new AuthorizationOutcome(decision, behavior.behaviorRisk());
        } catch (CoreUnavailableException e) {
            return failClosed("CONTEXT_SERVICE_UNAVAILABLE", requestId, e);
        } catch (BehaviorHistoryUnavailableException e) {
            return failClosed("BEHAVIOR_HISTORY_UNAVAILABLE", requestId, e);
        } catch (PromptRiskUnavailableException e) {
            return failClosed("PROMPT_RISK_UNAVAILABLE", requestId, e);
        } catch (AiUnavailableException e) {
            return failClosed("BEHAVIOR_RISK_UNAVAILABLE", requestId, e);
        } catch (OpaUnavailableException e) {
            return failClosed("POLICY_ENGINE_UNAVAILABLE", requestId, e);
        }
    }

    private RiskInput riskInput(PromptRiskSnapshot promptRisk, BehaviorRiskResult behavior) {
        if (promptRisk == null || promptRisk.promptRisk() == null) {
            throw new PromptRiskUnavailableException("Prompt risk snapshot is incomplete");
        }
        if (!"EVALUATED".equals(promptRisk.evaluationStatus())) {
            throw new PromptRiskUnavailableException("Prompt risk was not evaluated");
        }
        return new RiskInput(
            promptRisk.promptRisk().doubleValue(),
            promptRisk.detected(),
            behavior.behaviorRisk(),
            behavior.behaviorRiskLevel(),
            behavior.isAnomaly());
    }

    private AuthorizationOutcome failClosed(String reasonCode, String requestId, Exception cause) {
        log.warn("{} requestId={}", reasonCode, requestId, cause);
        return AuthorizationOutcome.failClosed(reasonCode);
    }
}
