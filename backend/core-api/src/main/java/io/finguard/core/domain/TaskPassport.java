package io.finguard.core.domain;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
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

/**
 * 현재 Case에서 Agent에게 유효한 최소권한 스냅샷. {@code docs/01-feature-spec.md} F05.
 *
 * <pre>
 * Employee Authority ∩ Permission Template ∩ Financial Case ∩ Consumer Mandate
 *     → Agent Effective Permission → Task Passport
 * </pre>
 *
 * <p>불변식: {@code Agent Effective Permission ⊆ Employee Authority} ({@code AGENTS.md}).
 *
 * <p>Agent는 내용을 수정하지 않고 {@code passportId}만 Runtime 요청에 쓴다. 발급은 이슈 #19,
 * 시드로 넣지 않는다 — 시드하면 계산기가 망가져도 데모가 성공해서 아무것도 증명하지 못한다.
 */
@Entity
@Table(name = "task_passports")
public class TaskPassport {

    /** 예: {@code PASS-001}. */
    @Id
    @Column(name = "passport_id", nullable = false, length = 64)
    private String passportId;

    @Column(name = "agent_id", nullable = false, length = 64)
    private String agentId;

    @Column(name = "employee_id", nullable = false, length = 64)
    private String employeeId;

    @Column(name = "case_id", nullable = false, length = 64)
    private String caseId;

    @Column(name = "consumer_id", nullable = false, length = 64)
    private String consumerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, length = 64)
    private TaskType taskType;

    @ElementCollection
    @CollectionTable(
            name = "task_passport_allowed_tools",
            joinColumns = @JoinColumn(name = "passport_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "tool", nullable = false, length = 64)
    private Set<Tool> allowedTools = EnumSet.noneOf(Tool.class);

    @ElementCollection
    @CollectionTable(
            name = "task_passport_allowed_data",
            joinColumns = @JoinColumn(name = "passport_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "data_type", nullable = false, length = 64)
    private Set<DataType> allowedData = EnumSet.noneOf(DataType.class);

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private TaskPassportStatus status;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Embedded
    private SourceVersions sourceVersions;

    protected TaskPassport() {
        // JPA
    }

    public TaskPassport(
            String passportId,
            String agentId,
            String employeeId,
            String caseId,
            String consumerId,
            TaskType taskType,
            Set<Tool> allowedTools,
            Set<DataType> allowedData,
            TaskPassportStatus status,
            Instant issuedAt,
            Instant expiresAt,
            SourceVersions sourceVersions) {
        this.passportId = passportId;
        this.agentId = agentId;
        this.employeeId = employeeId;
        this.caseId = caseId;
        this.consumerId = consumerId;
        this.taskType = taskType;
        this.allowedTools = EnumSet.noneOf(Tool.class);
        this.allowedTools.addAll(allowedTools);
        this.allowedData = EnumSet.noneOf(DataType.class);
        this.allowedData.addAll(allowedData);
        this.status = status;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.sourceVersions = sourceVersions;
    }

    public String getPassportId() {
        return passportId;
    }

    public String getAgentId() {
        return agentId;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public String getCaseId() {
        return caseId;
    }

    public String getConsumerId() {
        return consumerId;
    }

    public TaskType getTaskType() {
        return taskType;
    }

    public Set<Tool> getAllowedTools() {
        return Collections.unmodifiableSet(allowedTools);
    }

    public Set<DataType> getAllowedData() {
        return Collections.unmodifiableSet(allowedData);
    }

    public TaskPassportStatus getStatus() {
        return status;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public SourceVersions getSourceVersions() {
        return sourceVersions;
    }

    public boolean isUsableAt(Instant now) {
        return status == TaskPassportStatus.ACTIVE && now.isBefore(expiresAt);
    }
}
