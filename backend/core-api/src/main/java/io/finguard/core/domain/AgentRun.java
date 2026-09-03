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
 * 두고, {@code docs/04-api-contract.md} §3이 발급 엔드포인트를 Core에 정의한다. Backend 3는 호출만 한다.
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

    /**
     * 실행이 정상적으로 끝났다.
     *
     * <p>끝난 실행은 다시 바뀌지 않는다. 결론이 나중에 뒤집히면 그 사이에 내려진 판단들이 무엇을
     * 근거로 했는지 설명할 수 없게 된다 — {@link TaskPassport}가 발급 시점에 박혀 다시 안 바뀌는 것과
     * 같은 이유다.
     */
    public void complete() {
        transitionFromRunning(AgentRunStatus.COMPLETED);
    }

    /** 실행이 실패했다. 부르지 못한 경우도 포함한다 — 조용히 {@code RUNNING}으로 두지 않는다. */
    public void fail() {
        transitionFromRunning(AgentRunStatus.FAILED);
    }

    private void transitionFromRunning(AgentRunStatus next) {
        if (status != AgentRunStatus.RUNNING) {
            throw new IllegalStateException("AgentRun is no longer running: " + status);
        }
        this.status = next;
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
}
