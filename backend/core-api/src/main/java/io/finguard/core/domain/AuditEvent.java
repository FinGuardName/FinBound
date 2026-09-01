package io.finguard.core.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Business AuditEvent. {@code docs/04-api-contract.md} §11 · §14.
 *
 * <p><strong>인증 성공 이후에만 생성한다.</strong> 인증 실패는 {@link SecurityAuthEvent}로 따로 남긴다
 * ({@code docs/06-common-conventions.md} §10).
 *
 * <p>{@code requestId}에 UNIQUE 제약을 건다. {@code docs/04-api-contract.md} §17이 "동일 Request ID →
 * 실제 Downstream 실행 최대 1회"를 요구하는데, 이 제약이 있으면 INSERT 승자만 downstream을 호출하고
 * 패자는 저장된 상태를 반환하는 방식으로 멱등성이 나온다. Redis를 추가하지 않는 근거가 이것이다.
 * 제약이 조용히 빠지면 중복 금융 호출이 가능해지고, 중복 행이 쌓인 뒤에는 제약을 다시 걸 수도 없다.
 *
 * <p>원본 Prompt, 금융 문서, 금융 API 응답, Credential은 담지 않는다({@code docs/06} §24).
 * 여기 있는 것은 식별자·판정·점수·버전·시각뿐이다.
 */
@Entity
@Table(name = "audit_events")
public class AuditEvent {

    /** 예: {@code AUD-001}. */
    @Id
    @Column(name = "audit_event_id", nullable = false, length = 64)
    private String auditEventId;

    /** 멱등성의 근거. 유일해야 한다. */
    @Column(name = "request_id", nullable = false, unique = true, length = 64)
    private String requestId;

    @Column(name = "trace_id", length = 128)
    private String traceId;

    @Column(name = "agent_id", nullable = false, length = 64)
    private String agentId;

    @Column(name = "agent_run_id", nullable = false, length = 64)
    private String agentRunId;

    @Column(name = "case_id", length = 64)
    private String caseId;

    @Column(name = "target_consumer_id", length = 64)
    private String targetConsumerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "requested_tool", length = 64)
    private Tool requestedTool;

    /** 요청 시점이 아니라 Resolver가 Passport를 해석한 뒤에 확정된다. */
    @Column(name = "employee_id", length = 64)
    private String employeeId;

    @Column(name = "passport_id", length = 64)
    private String passportId;

    /**
     * Agent가 읽으려 시도한 Data. <strong>인가되었다는 증거가 아니다</strong> — 무엇을 시도했는지의
     * 기록이고, 허용 여부는 {@link #scopeStatus}의 {@code dataScope}가 말한다.
     */
    @ElementCollection
    @CollectionTable(
            name = "audit_event_requested_data",
            joinColumns = @JoinColumn(name = "audit_event_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "data_type", nullable = false, length = 64)
    private Set<DataType> requestedData = new LinkedHashSet<>();

    /** Resolver를 거치기 전에는 null이다. 9개가 전부 차거나 전부 비거나 둘 중 하나다. */
    @Embedded
    private AuditScopeStatus scopeStatus;

    @Column(name = "prompt_risk", precision = 5, scale = 4)
    private BigDecimal promptRisk;

    /** 점수가 없는 것과 검사하지 않은 것은 다른 사실이다(docs/04 §7). */
    @Enumerated(EnumType.STRING)
    @Column(name = "prompt_risk_evaluation_status", length = 32)
    private PromptRiskEvaluationStatus promptRiskEvaluationStatus;

    @Column(name = "prompt_model_version", length = 64)
    private String promptModelVersion;

    @Column(name = "behavior_risk", precision = 5, scale = 4)
    private BigDecimal behaviorRisk;

    /** 판정 전에는 비어 있다. PROCESSING 상태의 감사 기록은 결론이 없는 것이 정상이다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "decision", length = 16)
    private PolicyDecision decision;

    /** 같은 Reason Code가 두 번 붙는 것은 버그이므로 Set으로 둔다. 순서는 의미가 없다 — {@code .rego}가 정렬해서 돌려준다. */
    @ElementCollection
    @CollectionTable(
            name = "audit_event_reason_codes",
            joinColumns = @JoinColumn(name = "audit_event_id"))
    @Column(name = "reason_code", nullable = false, length = 64)
    private Set<String> reasonCodes = new LinkedHashSet<>();

    /** downstream에 도달했는가와 응답을 실제로 내보냈는가는 다른 사실이다. */
    @Column(name = "downstream_reached")
    private Boolean downstreamReached;

    @Column(name = "response_released")
    private Boolean responseReleased;

    /** downstream 실행 결과. PROCESSING 또는 downstream 미도달 상태에서는 비어 있을 수 있다. */
    @Column(name = "success")
    private Boolean success;

    @Column(name = "records_read")
    private Integer recordsRead;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "policy_version", length = 64)
    private String policyVersion;

    /** systemOutcome=ERROR일 때 어느 단계에서 실패했는지. contracts/audit/execution-outcome.schema.json. */
    @Column(name = "error_location", length = 64)
    private String errorLocation;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private AuditStatus status;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    /**
     * 낙관적 락. 한 감사 기록에 쓰는 주체가 선저장·증거 기록·최종 결과로 늘어나므로, 겹쳐 쓸 때
     * Hibernate의 전체 엔티티 UPDATE가 다른 쪽이 방금 쓴 칸을 옛 값으로 되돌리는 것을 막는다.
     */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected AuditEvent() {
        // JPA
    }

    /** 인증 성공 직후의 선저장. 이 저장이 실패하면 downstream을 호출하지 않는다. */
    public AuditEvent(
            String auditEventId,
            String requestId,
            String traceId,
            String agentId,
            String agentRunId,
            String caseId,
            String targetConsumerId,
            Tool requestedTool,
            Instant requestedAt) {
        this.auditEventId = auditEventId;
        this.requestId = requestId;
        this.traceId = traceId;
        this.agentId = agentId;
        this.agentRunId = agentRunId;
        // 어떤 업무의 어떤 도구였는지는 요청 시점에만 알 수 있다. 여기서 받지 않으면
        // Behavior History(docs/04 §9)가 caseId·tool 없이 나가 AI가 맥락을 잃는다.
        this.caseId = caseId;
        this.targetConsumerId = targetConsumerId;
        this.requestedTool = requestedTool;
        this.status = AuditStatus.PROCESSING;
        this.requestedAt = requestedAt;
    }

    /**
     * Resolver가 계산한 증거를 감사 기록에 남긴다. 선저장 때는 알 수 없던 값들이다.
     *
     * <p>확정된 기록에는 쓰지 않는다 — 판정이 끝난 뒤 근거가 바뀌면 그 기록은 더 이상 증거가 아니다.
     */
    public void recordResolvedContext(ResolvedAuditContext context) {
        if (status != AuditStatus.PROCESSING) {
            throw new IllegalStateException("AuditEvent is already finalized");
        }
        // 이미 근거가 적혀 있다면 set-once다. 같은 요청을 다시 해석해 근거가 통째로 같으면 재시도이므로
        // 아무것도 바꾸지 않고 통과시키고, 한 자리라도 다르면 거부한다.
        //
        // Scope 판정만 대조하면 부족하다. 판정이 OK로 같아도 대상 Passport나 Employee가 다르면
        // 그건 다른 사건이고, 덮어쓰는 순간 앞선 판정의 근거가 조용히 바뀐다.
        if (isEvidenceRecorded()) {
            if (!currentEvidence().matches(context)) {
                throw new IllegalStateException("AuditEvent already carries different evidence");
            }
            return;
        }
        this.employeeId = context.employeeId();
        this.passportId = context.passportId();
        this.requestedData.clear();
        this.requestedData.addAll(context.requestedData());
        this.scopeStatus = context.scopeStatus();
        this.promptRisk = context.promptRisk();
        this.promptRiskEvaluationStatus = context.promptRiskEvaluationStatus();
        this.promptModelVersion = context.promptModelVersion();
    }

    /**
     * 근거가 이미 적혔는가. {@code scopeStatus}로 판단한다 — 9개 컬럼이 전부 차거나 전부 비거나
     * 둘 중 하나이고({@code V3}의 all-or-none check), 근거를 적을 때 함께 채워지는 값이다.
     */
    private boolean isEvidenceRecorded() {
        return scopeStatus != null;
    }

    private ResolvedAuditContext currentEvidence() {
        return new ResolvedAuditContext(
                employeeId,
                passportId,
                requestedData,
                scopeStatus,
                promptRisk,
                promptRiskEvaluationStatus,
                promptModelVersion);
    }

    public String getAuditEventId() {
        return auditEventId;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public String getPassportId() {
        return passportId;
    }

    public Set<DataType> getRequestedData() {
        return Collections.unmodifiableSet(requestedData);
    }

    public AuditScopeStatus getScopeStatus() {
        return scopeStatus;
    }

    public PromptRiskEvaluationStatus getPromptRiskEvaluationStatus() {
        return promptRiskEvaluationStatus;
    }

    public String getPromptModelVersion() {
        return promptModelVersion;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getAgentId() {
        return agentId;
    }

    public String getAgentRunId() {
        return agentRunId;
    }

    public String getCaseId() {
        return caseId;
    }

    public String getTargetConsumerId() {
        return targetConsumerId;
    }

    public Tool getRequestedTool() {
        return requestedTool;
    }

    public BigDecimal getPromptRisk() {
        return promptRisk;
    }

    public BigDecimal getBehaviorRisk() {
        return behaviorRisk;
    }

    public PolicyDecision getDecision() {
        return decision;
    }

    public Set<String> getReasonCodes() {
        return Collections.unmodifiableSet(reasonCodes);
    }

    public Boolean getDownstreamReached() {
        return downstreamReached;
    }

    public Boolean getResponseReleased() {
        return responseReleased;
    }

    public Boolean getSuccess() {
        return success;
    }

    public Integer getRecordsRead() {
        return recordsRead;
    }

    public Long getLatencyMs() {
        return latencyMs;
    }

    public String getPolicyVersion() {
        return policyVersion;
    }

    public AuditStatus getStatus() {
        return status;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public String getErrorLocation() {
        return errorLocation;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    /** PROCESSING 기록에 최종 결과를 한 번만 적용한다. 감사 증거의 사후 덮어쓰기를 허용하지 않는다. */
    public void complete(AuditCompletion completion) {
        if (status != AuditStatus.PROCESSING) {
            throw new IllegalStateException("AuditEvent is already finalized");
        }
        if (completion.completedAt().isBefore(requestedAt)) {
            throw new IllegalArgumentException("completedAt must not precede requestedAt");
        }
        this.decision = completion.decision();
        this.reasonCodes.clear();
        this.reasonCodes.addAll(completion.reasonCodes());
        this.downstreamReached = completion.downstreamReached();
        this.responseReleased = completion.responseReleased();
        this.success = completion.success();
        this.recordsRead = completion.recordsRead();
        this.latencyMs = completion.latencyMs();
        this.errorLocation = completion.errorLocation();
        this.behaviorRisk = completion.behaviorRisk();
        this.policyVersion = completion.policyVersion();
        this.status = completion.systemOutcome();
        this.completedAt = completion.completedAt();
    }
}
