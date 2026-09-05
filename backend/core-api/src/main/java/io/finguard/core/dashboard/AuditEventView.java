package io.finguard.core.dashboard;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.finguard.core.domain.AuditEvent;
import io.finguard.core.domain.AuditScopeStatus;
import io.finguard.core.domain.AuditStatus;
import io.finguard.core.domain.DataType;
import io.finguard.core.domain.PolicyDecision;
import io.finguard.core.domain.PromptRiskEvaluationStatus;
import io.finguard.core.domain.PromptRiskLevel;
import io.finguard.core.domain.Severity;
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
 *
 * <p><strong>null은 키째로 뺀다.</strong> 스키마의 선택적 속성은 대부분 enum이거나 타입이 정해져 있어
 * null을 값으로 허용하지 않고, BLOCK의 실행 측정값 셋은 값이 아니라 <em>키의 존재 자체</em>를 금지한다
 * ({@code "not": {"anyOf": [{"required": [...]}]}}). JSON Schema의 {@code required}는 값이 null이어도
 * "있음"으로 보므로 {@code "success": null}도 위반이다. Jackson 기본값은 키를 남기므로 여기서 끈다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
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
        PromptRiskLevel promptRiskLevel,
        String promptModelVersion,
        BigDecimal behaviorRisk,
        Severity severity,
        Boolean riskFlagged,
        PolicyDecision decision,
        Set<String> reasonCodes,
        String policyVersion,
        AuditStatus systemOutcome,
        Boolean downstreamReached,
        Boolean responseReleased,
        Boolean success,
        Integer recordsRead,
        Long latencyMs,
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
                requestedDataOf(event),
                event.getScopeStatus(),
                event.getPromptRiskEvaluationStatus(),
                event.getPromptRisk(),
                event.getPromptRiskLevel(),
                event.getPromptModelVersion(),
                event.getBehaviorRisk(),
                event.getSeverity(),
                event.getRiskFlagged(),
                event.getDecision(),
                Set.copyOf(event.getReasonCodes()),
                event.getPolicyVersion(),
                systemOutcomeOf(event),
                event.getDownstreamReached(),
                event.getResponseReleased(),
                blocked(event) ? null : event.getSuccess(),
                blocked(event) ? null : event.getRecordsRead(),
                blocked(event) ? null : event.getLatencyMs(),
                event.getErrorLocation(),
                event.getStatus(),
                event.getRequestedAt(),
                event.getCompletedAt());
    }

    /**
     * Resolver를 거치기 전에는 요청 자료가 없다.
     *
     * <p>{@code audit-event.schema.json}이 {@code requestedData}에 {@code minItems: 1}을 건다. 빈
     * 배열은 스키마 위반이면서 동시에 <strong>"요청한 자료가 없다"는 거짓 사실</strong>이다 — 아직
     * 해석되지 않은 것과 해석했더니 비어 있는 것은 다르다. 값이 없으면 키를 만들지 않는다.
     */
    private static Set<DataType> requestedDataOf(AuditEvent event) {
        Set<DataType> requestedData = event.getRequestedData();
        return requestedData.isEmpty() ? null : Set.copyOf(requestedData);
    }

    /**
     * BLOCK 기록은 실행 측정값을 내보내지 않는다.
     *
     * <p>{@code contracts/audit/audit-event.schema.json}이 BLOCK에서 {@code success}·
     * {@code recordsRead}·{@code latencyMs}를 금지한다. downstream에 닿지 않았으니 측정할 것이 없었다.
     *
     * <p>저장 쪽에서도 막지만({@code AuditOutcomeRequest}) 여기서 한 번 더 거른다 — 계약이 생기기 전에
     * 쌓인 기록에는 값이 남아 있을 수 있고, 화면에 나가는 순간 그것도 계약 위반이다.
     */
    private static boolean blocked(AuditEvent event) {
        return event.getDecision() == PolicyDecision.BLOCK;
    }

    /**
     * 진행 중인 요청에는 시스템 결과가 없다.
     *
     * <p>엔티티는 {@code status} 한 컬럼에 둘을 접어 두었지만 계약은 두 속성으로 나눈다 —
     * {@code status}는 {@code PROCESSING|COMPLETED|ERROR}이고 {@code systemOutcome}은
     * {@code COMPLETED|ERROR}뿐이다({@code contracts/audit/audit-event.schema.json}).
     * 접힌 값을 그대로 두 자리에 넣으면 PROCESSING 기록이 스키마를 위반한 채 나간다.
     */
    private static AuditStatus systemOutcomeOf(AuditEvent event) {
        return event.getStatus() == AuditStatus.PROCESSING ? null : event.getStatus();
    }
}
