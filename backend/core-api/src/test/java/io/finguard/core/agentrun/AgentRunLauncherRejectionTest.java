package io.finguard.core.agentrun;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import org.junit.jupiter.api.Test;

import io.finguard.core.domain.AgentRun;
import io.finguard.core.domain.AgentRunStatus;
import io.finguard.core.domain.AgentSimulationScenario;

/**
 * 실행 대기열이 가득 찼을 때.
 *
 * <p>이 경우가 이 클래스 전체의 존재 이유를 무효화한다. 대기열이 거절하면 Agent를 부르지 못하는데,
 * 그 사실을 아무도 기록하지 않으면 AgentRun이 <strong>영원히 {@code RUNNING}으로 남는다</strong> —
 * 없애려던 바로 그 상태다. 게다가 트랜잭션은 이미 커밋됐고 호출자는 201을 받은 뒤라,
 * 예외를 던져도 아무에게도 닿지 않는다.
 */
class AgentRunLauncherRejectionTest {

    private static final Instant STARTED = Instant.parse("2026-08-25T10:00:00Z");

    @Test
    void marksTheRunFailedWhenTheQueueRefusesTheWork() {
        AgentRun run = running();
        AgentRunLauncher launcher =
                new AgentRunLauncher(
                        new RecordingOutcomes(run),
                        (agentRunId, passportId, scenario) -> { },
                        alwaysRejects());

        launcher.onAgentRunCreated(
                new AgentRunCreated("RUN-900", "PASS-900", AgentSimulationScenario.NORMAL_CREDIT_SCORE));

        assertThat(run.getStatus()).isEqualTo(AgentRunStatus.FAILED);
    }

    private static Executor alwaysRejects() {
        return task -> {
            throw new RejectedExecutionException("queue is full");
        };
    }

    private AgentRun running() {
        return new AgentRun(
                "RUN-900",
                "LOAN-AGENT-01",
                "EMP-900",
                "LOAN-2026-900",
                "PASS-900",
                List.of("INPUT-900"),
                AgentRunStatus.RUNNING,
                STARTED);
    }

    private record RecordingOutcomes(AgentRun run) implements AgentRunOutcomes {

        @Override
        public void complete(String agentRunId) {
            lookup(agentRunId).ifPresent(AgentRun::complete);
        }

        @Override
        public void fail(String agentRunId) {
            lookup(agentRunId).ifPresent(AgentRun::fail);
        }

        private Optional<AgentRun> lookup(String agentRunId) {
            return run.getAgentRunId().equals(agentRunId) ? Optional.of(run) : Optional.empty();
        }
    }
}
