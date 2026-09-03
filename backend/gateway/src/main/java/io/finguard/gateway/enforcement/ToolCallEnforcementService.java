package io.finguard.gateway.enforcement;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.finguard.gateway.authorization.AuthorizationOutcome;
import io.finguard.gateway.authorization.AuthorizationService;
import io.finguard.gateway.client.CoreClient;
import io.finguard.gateway.client.DownstreamClient;
import io.finguard.gateway.contract.PolicyDecision;
import io.finguard.gateway.dto.AuditOutcome;
import io.finguard.gateway.dto.AuditStart;
import io.finguard.gateway.dto.DownstreamToolResult;
import io.finguard.gateway.dto.ToolCallRequest;
import io.finguard.gateway.dto.ToolCallResponse;
import io.finguard.gateway.exception.AuditWriteException;
import io.finguard.gateway.exception.DownstreamTimeoutException;
import io.finguard.gateway.exception.DownstreamUnavailableException;
import io.finguard.gateway.exception.DuplicateRequestException;
import io.finguard.gateway.identity.VerifiedAgentIdentity;
import lombok.extern.slf4j.Slf4j;

/**
 * Runtime Tool Call의 실제 집행. Audit 선저장 → Authorization → Downstream → Outcome PATCH
 * 순으로 진행하며 어느 단계 실패든 fail-closed 처리한다.
 *
 * <p>정책 판정에 닿기 전 시스템 장애로 차단된 경우는 정책 BLOCK이 아니라 systemOutcome=ERROR로만 기록한다.
 * decision을 함께 보내면 contracts/audit/execution-outcome.schema.json의 BLOCK 절과 ERROR 절이 충돌한다
 * (BLOCK은 success/recordsRead/latencyMs가 없어야 하지만 ERROR는 success=false를 필수로 요구).
 */
@Slf4j
@Service
public class ToolCallEnforcementService {

    private static final Set<String> SYSTEM_FAILURE_REASONS = Set.of(
        "CONTEXT_SERVICE_UNAVAILABLE",
        "BEHAVIOR_HISTORY_UNAVAILABLE",
        "PROMPT_RISK_UNAVAILABLE",
        "BEHAVIOR_RISK_UNAVAILABLE",
        "POLICY_ENGINE_UNAVAILABLE");

    private final Cache<String, EnforcementResult> completedResponses = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofMinutes(10))
        .maximumSize(10_000)
        .build();
    private final ConcurrentMap<String, Boolean> inFlightRequests = new ConcurrentHashMap<>();

    private final AuthorizationService authorizationService;
    private final CoreClient coreClient;
    private final DownstreamClient downstreamClient;
    private final Clock clock;

    public ToolCallEnforcementService(AuthorizationService authorizationService,
                                      CoreClient coreClient,
                                      DownstreamClient downstreamClient,
                                      Clock clock) {
        this.authorizationService = authorizationService;
        this.coreClient = coreClient;
        this.downstreamClient = downstreamClient;
        this.clock = clock;
    }

    public EnforcementResult enforce(VerifiedAgentIdentity identity,
                                     ToolCallRequest request,
                                     String requestId,
                                     String traceparent) {
        EnforcementResult cached = completedResponses.getIfPresent(requestId);
        if (cached != null) {
            return cached;
        }
        if (inFlightRequests.putIfAbsent(requestId, Boolean.TRUE) != null) {
            return block(requestId, "DUPLICATE_REQUEST");
        }

        try {
            return executeFirstAttempt(identity, request, requestId, traceparent);
        } finally {
            inFlightRequests.remove(requestId);
        }
    }

    private EnforcementResult executeFirstAttempt(VerifiedAgentIdentity identity,
                                                  ToolCallRequest request,
                                                  String requestId,
                                                  String traceparent) {
        Instant requestedAt = clock.instant();
        try {
            coreClient.createAudit(
                identity,
                auditStart(identity, request, requestId, traceparent, requestedAt),
                traceparent);
        } catch (AuditWriteException e) {
            log.warn("Audit create failed requestId={}", requestId, e);
            return block(requestId, "AUDIT_WRITE_FAILED");
        } catch (DuplicateRequestException e) {
            log.warn("Duplicate request rejected before authorization requestId={}", requestId, e);
            return block(requestId, "DUPLICATE_REQUEST");
        }

        AuthorizationOutcome outcome = authorizationService.decide(
            identity, request, requestId, traceparent, requestedAt);
        if (!outcome.isAllow()) {
            EnforcementResult result = block(requestId, outcome.reasonCodes());
            safeUpdateOutcome(identity, requestId, traceparent, blockOutcome(outcome, requestedAt));
            completedResponses.put(requestId, result);
            return result;
        }

        return executeAllowedDownstream(identity, request, requestId, traceparent, outcome, requestedAt);
    }

    private EnforcementResult executeAllowedDownstream(VerifiedAgentIdentity identity,
                                                       ToolCallRequest request,
                                                       String requestId,
                                                       String traceparent,
                                                       AuthorizationOutcome outcome,
                                                       Instant requestedAt) {
        Instant downstreamStarted = clock.instant();
        try {
            DownstreamToolResult downstream = downstreamClient.execute(request, requestId, traceparent);
            long latencyMs = Duration.between(downstreamStarted, clock.instant()).toMillis();
            EnforcementResult result = new EnforcementResult(
                HttpStatus.OK,
                ToolCallResponse.allow(requestId, downstreamResult(downstream)));
            safeUpdateOutcome(identity, requestId, traceparent, allowOutcome(outcome, requestedAt, latencyMs));
            completedResponses.put(requestId, result);
            return result;
        } catch (DownstreamUnavailableException e) {
            log.warn("Downstream failed requestId={} reached={}", requestId, e.downstreamReached(), e);
            return recordDownstreamError(
                identity, requestId, traceparent, outcome, requestedAt,
                "DOWNSTREAM_ERROR", e.downstreamReached(), HttpStatus.BAD_GATEWAY);
        } catch (DownstreamTimeoutException e) {
            log.warn("Downstream timed out requestId={}", requestId, e);
            return recordDownstreamError(
                identity, requestId, traceparent, outcome, requestedAt,
                "DOWNSTREAM_TIMEOUT", true, HttpStatus.GATEWAY_TIMEOUT);
        }
    }

    private EnforcementResult recordDownstreamError(VerifiedAgentIdentity identity,
                                                    String requestId,
                                                    String traceparent,
                                                    AuthorizationOutcome outcome,
                                                    Instant requestedAt,
                                                    String reasonCode,
                                                    boolean downstreamReached,
                                                    HttpStatus status) {
        safeUpdateOutcome(identity, requestId, traceparent,
            downstreamErrorOutcome(outcome, requestedAt, reasonCode, downstreamReached));
        EnforcementResult result = new EnforcementResult(
            status,
            ToolCallResponse.systemError(requestId, reasonCode, List.of(reasonCode)));
        completedResponses.put(requestId, result);
        return result;
    }

    private AuditStart auditStart(VerifiedAgentIdentity identity,
                                  ToolCallRequest request,
                                  String requestId,
                                  String traceparent,
                                  Instant requestedAt) {
        return new AuditStart(
            requestId,
            traceparent,
            request.agentRunId(),
            identity.agentId(),
            null,
            request.targetConsumerId(),
            request.tool(),
            "PROCESSING",
            requestedAt);
    }

    private AuditOutcome blockOutcome(AuthorizationOutcome outcome, Instant completedAt) {
        List<String> reasonCodes = outcome.reasonCodes();
        boolean systemFailure = hasSystemFailure(reasonCodes);
        // systemOutcome=ERROR와 decision=BLOCK은 execution-outcome 스키마에서 상호 배타적이다.
        // BLOCK 절은 success/recordsRead/latencyMs가 없어야 하지만 ERROR 절은 success=false를 요구한다.
        // 판정에 닿지 못한 fail-closed는 decision을 비워 systemOutcome=ERROR 단일 기록으로 남긴다.
        return new AuditOutcome(
            systemFailure ? null : PolicyDecision.BLOCK,
            systemFailure ? "ERROR" : "COMPLETED",
            Set.copyOf(reasonCodes),
            false,
            false,
            systemFailure ? Boolean.FALSE : null,
            null,
            null,
            systemFailure ? errorLocation(reasonCodes) : null,
            behaviorRisk(outcome),
            systemFailure ? null : outcome.severity(),
            systemFailure ? null : outcome.riskFlagged(),
            outcome.policyVersion(),
            completedAt);
    }

    private AuditOutcome allowOutcome(AuthorizationOutcome outcome, Instant completedAt, long latencyMs) {
        return new AuditOutcome(
            PolicyDecision.ALLOW,
            "COMPLETED",
            Set.of(),
            true,
            true,
            true,
            1,
            latencyMs,
            null,
            behaviorRisk(outcome),
            outcome.severity(),
            outcome.riskFlagged(),
            outcome.policyVersion(),
            completedAt);
    }

    private AuditOutcome downstreamErrorOutcome(AuthorizationOutcome outcome,
                                                Instant completedAt,
                                                String reasonCode,
                                                boolean downstreamReached) {
        return new AuditOutcome(
            PolicyDecision.ALLOW,
            "ERROR",
            Set.of(reasonCode),
            downstreamReached,
            false,
            false,
            null,
            null,
            "MOCK_FINANCE",
            behaviorRisk(outcome),
            outcome.severity(),
            outcome.riskFlagged(),
            outcome.policyVersion(),
            completedAt);
    }

    private void safeUpdateOutcome(VerifiedAgentIdentity identity,
                                   String requestId,
                                   String traceparent,
                                   AuditOutcome outcome) {
        try {
            coreClient.updateAuditOutcome(identity, requestId, outcome, traceparent);
        } catch (AuditWriteException e) {
            log.error("Audit outcome update failed requestId={}", requestId, e);
        }
    }

    private EnforcementResult block(String requestId, String reasonCode) {
        return block(requestId, List.of(reasonCode));
    }

    private EnforcementResult block(String requestId, List<String> reasonCodes) {
        return new EnforcementResult(HttpStatus.FORBIDDEN, ToolCallResponse.block(requestId, reasonCodes));
    }

    private Map<String, Object> downstreamResult(DownstreamToolResult downstream) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tool", downstream.tool());
        result.put("consumerId", downstream.consumerId());
        result.putAll(downstream.result());
        return result;
    }

    private boolean hasSystemFailure(List<String> reasonCodes) {
        return reasonCodes.stream().anyMatch(SYSTEM_FAILURE_REASONS::contains);
    }

    private String errorLocation(List<String> reasonCodes) {
        if (reasonCodes.contains("CONTEXT_SERVICE_UNAVAILABLE")
                || reasonCodes.contains("BEHAVIOR_HISTORY_UNAVAILABLE")
                || reasonCodes.contains("PROMPT_RISK_UNAVAILABLE")) {
            return "CORE";
        }
        if (reasonCodes.contains("BEHAVIOR_RISK_UNAVAILABLE")) {
            return "AI_RISK";
        }
        if (reasonCodes.contains("POLICY_ENGINE_UNAVAILABLE")) {
            return "OPA";
        }
        return null;
    }

    private BigDecimal behaviorRisk(AuthorizationOutcome outcome) {
        return outcome.behaviorRisk() == null ? null : BigDecimal.valueOf(outcome.behaviorRisk());
    }
}
