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
            PromptRiskSnapshot promptRisk = requireEvaluatedPromptRisk(
                resolvedContext.promptRiskSnapshot());
            BehaviorHistory history = coreClient.behaviorHistory(identity, "5m", requestId, traceparent);
            BehaviorRiskResult behavior = aiClient.evaluateBehavior(
                identity, request, resolvedContext, history, requestId, traceparent, requestedAt);
            logBehaviorEvidence(requestId, traceparent, history, behavior);
            AuthorizationContext context = new AuthorizationContext(
                requestId,
                resolvedContext.scopeStatus(),
                riskInput(promptRisk, behavior),
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

    private PromptRiskSnapshot requireEvaluatedPromptRisk(PromptRiskSnapshot promptRisk) {
        if (promptRisk == null
                || promptRisk.promptRisk() == null
                || promptRisk.riskLevel() == null) {
            throw new PromptRiskUnavailableException("Prompt risk snapshot is incomplete");
        }
        if (!"EVALUATED".equals(promptRisk.evaluationStatus())) {
            throw new PromptRiskUnavailableException("Prompt risk was not evaluated");
        }
        return promptRisk;
    }

    private RiskInput riskInput(PromptRiskSnapshot promptRisk, BehaviorRiskResult behavior) {
        return new RiskInput(
            promptRisk.promptRisk().doubleValue(),
            promptRisk.riskLevel(),
            promptRisk.detected(),
            behavior.behaviorRisk(),
            behavior.behaviorRiskLevel(),
            behavior.isAnomaly());
    }

    /**
     * AI 가 무엇을 판단했는지 남긴다. 감사 기록에는 {@code behaviorRisk} 숫자만 저장되므로
     * <strong>0.0 이 "모델이 정상이라 판단함" 인지 "모델이 아예 안 돌았음" 인지 구분할 수 없다.</strong>
     * cold start 분기는 추론 없이 0.0 과 모델 버전을 함께 돌려주기 때문이다
     * ({@code ai-risk/app/behavior/service.py} 의 {@code COLD_START_MIN_EVENTS}).
     *
     * <p>이력이 몇 건 들어갔는지도 함께 남긴다. 실제로 이 값이 0 인 채로 오래 방치돼
     * 행동 이상 관문이 한 번도 발동하지 못한 적이 있다(이슈 #117).
     *
     * <p>감사 기록에 등급·상태를 저장하는 것이 근본 해법이지만 그것은 계약 변경이라 따로 간다.
     * 여기서는 <strong>운영 진단만</strong> 연다 — 이 로그가 없다는 것이 추론이 돌지 않았다는
     * 증거는 아니다. 점수나 원문 금융 데이터는 남기지 않는다({@code docs/06} §26).
     */
    private void logBehaviorEvidence(String requestId,
                                     String traceparent,
                                     BehaviorHistory history,
                                     BehaviorRiskResult behavior) {
        log.info(
            "behaviorEvaluated requestId={} traceparent={} historyStatus={} historySize={} "
                + "level={} anomaly={} featureVersion={} modelVersion={}",
            requestId,
            traceparent,
            behavior.historyStatus(),
            history.completedEvents() == null ? 0 : history.completedEvents().size(),
            behavior.behaviorRiskLevel(),
            behavior.isAnomaly(),
            behavior.featureVersion(),
            behavior.modelVersion());
    }

    private AuthorizationOutcome failClosed(String reasonCode, String requestId, Exception cause) {
        log.warn("{} requestId={}", reasonCode, requestId, cause);
        return AuthorizationOutcome.failClosed(reasonCode);
    }
}
