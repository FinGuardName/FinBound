package io.finguard.agent.agentrun.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

class AgentRunTest {
    private static final OffsetDateTime STARTED_AT = OffsetDateTime.parse(
            "2026-08-25T10:00:00+09:00"
    );
    private static final OffsetDateTime COMPLETED_AT = OffsetDateTime.parse(
            "2026-08-25T10:05:00+09:00"
    );

    @Test
    void startsAndCompletesAgentRun() {
        AgentRun agentRun = createdAgentRun();

        agentRun.start(
                new AgentRunContext("LOAN-2026-001", "PASS-001"),
                new SecuredInputReference("INPUT-001", "sha256:internal-only"),
                STARTED_AT
        );
        agentRun.complete(COMPLETED_AT);

        assertThat(agentRun.status()).isEqualTo(AgentRunStatus.COMPLETED);
        assertThat(agentRun.caseId()).isEqualTo("LOAN-2026-001");
        assertThat(agentRun.passportId()).isEqualTo("PASS-001");
        assertThat(agentRun.inputRefs()).containsExactly("INPUT-001");
        assertThat(agentRun.startedAt()).isEqualTo(STARTED_AT);
        assertThat(agentRun.completedAt()).isEqualTo(COMPLETED_AT);
    }

    @Test
    void failsCreatedAgentRun() {
        AgentRun agentRun = createdAgentRun();

        agentRun.fail(COMPLETED_AT);

        assertThat(agentRun.status()).isEqualTo(AgentRunStatus.FAILED);
        assertThat(agentRun.completedAt()).isEqualTo(COMPLETED_AT);
    }

    @Test
    void rejectsInvalidStatusTransitions() {
        AgentRun agentRun = createdAgentRun();

        assertThatThrownBy(() -> agentRun.complete(COMPLETED_AT))
                .isInstanceOf(IllegalStateException.class);

        agentRun.fail(COMPLETED_AT);

        assertThatThrownBy(() -> agentRun.fail(COMPLETED_AT))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> agentRun.start(
                new AgentRunContext("LOAN-2026-001", "PASS-001"),
                new SecuredInputReference("INPUT-001", "sha256:internal-only"),
                STARTED_AT
        )).isInstanceOf(IllegalStateException.class);
    }

    private AgentRun createdAgentRun() {
        AgentRun agentRun = AgentRun.created("RUN-001", "LOAN-AGENT-01", "EMP-101");
        assertThat(agentRun.agentRunId()).isEqualTo("RUN-001");
        assertThat(agentRun.agentId()).isEqualTo("LOAN-AGENT-01");
        assertThat(agentRun.employeeId()).isEqualTo("EMP-101");
        assertThat(agentRun.status()).isEqualTo(AgentRunStatus.CREATED);
        return agentRun;
    }
}
