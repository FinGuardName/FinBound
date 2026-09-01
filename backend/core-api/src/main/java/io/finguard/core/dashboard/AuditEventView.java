package io.finguard.core.dashboard;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

import io.finguard.core.domain.AuditEvent;
import io.finguard.core.domain.AuditScopeStatus;
import io.finguard.core.domain.AuditStatus;
import io.finguard.core.domain.DataType;
import io.finguard.core.domain.PolicyDecision;
import io.finguard.core.domain.PromptRiskEvaluationStatus;
import io.finguard.core.domain.Tool;

/**
 * Dashboard가 읽는 AuditEvent 한 건. {@code contracts/audit/audit-event.schema.json}의 이름을 그대로 쓴다.
 *
 * <p>원본 Prompt·금융 문서·금융 응답·Credential은 담지 않는다({@code docs/06} §24). 엔티티에 애초에
 * 없는 값들이라 여기서 새로 빠뜨릴 것도 없다.
 *
 * <p>{@code status}와 {@code systemOutcome}을 둘 다 내보낸다. 엔티티는 한 컬럼에 접어 두었지만
 * 계약은 두 속성으로 정의하고, 프론트는 {@code systemOutcome}으로 ERROR를 가려낸다.
 *
 * <p>behavior 등급·버전 3개는 여기 없다. 저장 칸은 {@code V3}가 만들었지만 Gateway가 실어 보내는
 * 경로가 아직 없어 항상 null이다 — 없는 값을 키만 만들어 내보내면 "채워졌는데 비었다"로 읽힌다.
 */
public record AuditEventView(
        String auditEventId,
        String requestId,
        String traceId,
        String agentId,
        String agentRunId,
        String employeeId,
        String caseId,
        String passportId,
        String targetConsumerId,
        Tool requestedTool,
        Set<DataType> requestedData,
        AuditScopeStatus scopeStatus,
        PromptRiskEvaluationStatus promptRiskEvaluationStatus,
        BigDecimal promptRisk,
        String promptModelVersion,
        BigDecimal behaviorRisk,
        PolicyDecision decision,
        Set<String> reasonCodes,
        String policyVersion,
        AuditStatus systemOutcome,
        Boolean downstreamReached,
        Boolean responseReleased,
        String errorLocation,
        AuditStatus status,
        Instant requestedAt,
        Instant completedAt) {

    /**
     * 반드시 트랜잭션 안에서 부른다. {@code reasonCodes}·{@code requestedData}는 lazy
     * {@code @ElementCollection}이라 여기서 복사해 두지 않으면 직렬화 시점에
     * {@code LazyInitializationException}이 난다 — 세션은 이미 닫혀 있다.
     */
    public static AuditEventView of(AuditEvent event) {
        return new AuditEventView(
                event.getAuditEventId(),
                event.getRequestId(),
                event.getTraceId(),
                event.getAgentId(),
                event.getAgentRunId(),
                event.getEmployeeId(),
                event.getCaseId(),
                event.getPassportId(),
                event.getTargetConsumerId(),
                event.getRequestedTool(),
                Set.copyOf(event.getRequestedData()),
                event.getScopeStatus(),
                event.getPromptRiskEvaluationStatus(),
                event.getPromptRisk(),
                event.getPromptModelVersion(),
                event.getBehaviorRisk(),
                event.getDecision(),
                Set.copyOf(event.getReasonCodes()),
                event.getPolicyVersion(),
                event.getStatus(),
                event.getDownstreamReached(),
                event.getResponseReleased(),
                event.getErrorLocation(),
                event.getStatus(),
                event.getRequestedAt(),
                event.getCompletedAt());
    }
}
