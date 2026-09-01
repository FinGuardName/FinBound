package io.finguard.core.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

/**
 * Agent 실행 단위. {@code docs/04-api-contract.md} §4.2.
 *
 * <p>Core가 소유한다 — {@code docs/02-architecture.md} §7.1이 AgentRun과 TaskPassport를 Core 책임으로
 * 두고, {@code docs/04-api-contract.md} §3이 발급 엔드포인트를 Core에 정의한다. Agent는 Core가
 * 전달한 실행 참조만 소비한다.
 */
@Entity
@Table(name = "agent_runs")
public class AgentRun {

    /** 예: {@code RUN-001}. */
    @Id
    @Column(name = "agent_run_id", nullable = false, length = 64)
    private String agentRunId;

    @Column(name = "agent_id", nullable = false, length = 64)
    private String agentId;

    @Column(name = "employee_id", nullable = false, length = 64)
    private String employeeId;

    @Column(name = "case_id", nullable = false, length = 64)
    private String caseId;

    @Column(name = "passport_id", nullable = false, length = 64)
    private String passportId;

    /** {@link SecuredAgentInput}의 식별자들. 원문은 담지 않는다. */
    @ElementCollection
    @CollectionTable(name = "agent_run_input_refs", joinColumns = @JoinColumn(name = "agent_run_id"))
    @OrderColumn(name = "input_ref_order")
    @Column(name = "input_ref", nullable = false, length = 64)
    private List<String> inputRefs = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private AgentRunStatus status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    protected AgentRun() {
        // JPA
    }

    public AgentRun(
            String agentRunId,
            String agentId,
            String employeeId,
            String caseId,
            String passportId,
            List<String> inputRefs,
            AgentRunStatus status,
            Instant startedAt) {
        this.agentRunId = agentRunId;
        this.agentId = agentId;
        this.employeeId = employeeId;
        this.caseId = caseId;
        this.passportId = passportId;
        this.inputRefs = new ArrayList<>(inputRefs);
        this.status = status;
        this.startedAt = startedAt;
    }

    public String getAgentRunId() {
        return agentRunId;
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

    public String getPassportId() {
        return passportId;
    }

    public List<String> getInputRefs() {
        return Collections.unmodifiableList(inputRefs);
    }

    public AgentRunStatus getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void fail() {
        if (status == AgentRunStatus.RUNNING) {
            status = AgentRunStatus.FAILED;
        }
    }
}
