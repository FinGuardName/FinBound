package io.finguard.core.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * 현재 Agent가 수행 중인 금융업무. {@code docs/01-feature-spec.md} F04.
 *
 * <p><strong>이 Case의 Consumer가 Runtime Tool Call의 고객 Scope 기준이 된다</strong>
 * ({@code docs/01-feature-spec.md}:172). 직원 권한이 더 넓더라도 현재 Case 밖 고객은 차단된다 —
 * 이번 사이클 데모의 핵심이다.
 *
 * <p>Agent는 Runtime 요청 본문으로 이 내용을 재정의할 수 없다.
 */
@Entity
@Table(name = "financial_cases")
public class FinancialCase {

    /** 예: {@code LOAN-2026-001}. */
    @Id
    @Column(name = "case_id", nullable = false, length = 64)
    private String caseId;

    @Column(name = "employee_id", nullable = false, length = 64)
    private String employeeId;

    @Column(name = "consumer_id", nullable = false, length = 64)
    private String consumerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, length = 64)
    private TaskType taskType;

    @Column(name = "template_id", nullable = false, length = 64)
    private String templateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private FinancialCaseStatus status;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected FinancialCase() {
        // JPA
    }

    public FinancialCase(
            String caseId,
            String employeeId,
            String consumerId,
            TaskType taskType,
            String templateId,
            FinancialCaseStatus status,
            Instant issuedAt,
            Instant expiresAt) {
        this.caseId = caseId;
        this.employeeId = employeeId;
        this.consumerId = consumerId;
        this.taskType = taskType;
        this.templateId = templateId;
        this.status = status;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
    }

    public String getCaseId() {
        return caseId;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public String getConsumerId() {
        return consumerId;
    }

    public TaskType getTaskType() {
        return taskType;
    }

    public String getTemplateId() {
        return templateId;
    }

    public FinancialCaseStatus getStatus() {
        return status;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public long getVersion() {
        return version;
    }

    /** 비활성이거나 만료되면 Tool Call을 차단한다 (F04 처리 규칙). */
    public boolean isUsableAt(Instant now) {
        return status == FinancialCaseStatus.ACTIVE && now.isBefore(expiresAt);
    }
}
