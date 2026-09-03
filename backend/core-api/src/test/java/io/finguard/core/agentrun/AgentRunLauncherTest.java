package io.finguard.core.agentrun;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.finguard.core.domain.AgentRun;
import io.finguard.core.domain.AgentRunStatus;
import io.finguard.core.domain.AgentSimulationScenario;

/**
 * AgentRun이 저장된 뒤 Agent를 깨우고 결과로 상태를 갱신하는 부분.
 *
 * <p>실행기는 같은 스레드에서 바로 돌린다. 여기서 볼 것은 "무엇을 보냈고 결과를 어떻게 반영했는가"이고,
 * 스레드 전환은 {@code AsyncConfig}의 문제다.
 */
class AgentRunLauncherTest {

    private static final Instant STARTED = Instant.parse("2026-08-25T10:00:00Z");

    @Test
    void completesTheRunWhenTheAgentAnswers() {
        AgentRun run = running();
        RecordingClient client = new RecordingClient(null);
        launcherFor(run, client)
                .onAgentRunCreated(created(AgentSimulationScenario.NORMAL_CREDIT_SCORE));

        assertThat(client.agentRunId).isEqualTo("RUN-900");
        assertThat(client.passportId).isEqualTo("PASS-900");
        assertThat(run.getStatus()).isEqualTo(AgentRunStatus.COMPLETED);
    }

    @Test
    void failsTheRunWhenTheAgentCannotBeReached() {
        AgentRun run = running();
        launcherFor(
                        run,
                        new RecordingClient(
                                new AgentSimulationFailedException("RUN-900", new IllegalStateException("boom"))))
                .onAgentRunCreated(created(AgentSimulationScenario.CASE_SCOPE_ATTACK));

        // 부르지 못한 실행을 RUNNING으로 두면 영원히 진행 중으로 남는다.
        assertThat(run.getStatus()).isEqualTo(AgentRunStatus.FAILED);
    }

    @Test
    void failsTheRunOnAnyUnexpectedError() {
        AgentRun run = running();
        // AgentSimulationFailedException만 잡으면 다른 예외에서 실행이 RUNNING으로 남는다.
        launcherFor(run, new RecordingClient(new IllegalArgumentException("unexpected")))
                .onAgentRunCreated(created(AgentSimulationScenario.NORMAL_CREDIT_SCORE));

        assertThat(run.getStatus()).isEqualTo(AgentRunStatus.FAILED);
    }

    @Test
    void carriesTheRequestedScenarioToTheAgent() {
        AgentRun run = running();
        RecordingClient client = new RecordingClient(null);
        launcherFor(run, client).onAgentRunCreated(created(AgentSimulationScenario.CASE_SCOPE_ATTACK));

        // 시나리오를 바꿔치면 공격 시연이 정상 조회가 된다. 그대로 전달되어야 한다.
        assertThat(client.scenario).isEqualTo(AgentSimulationScenario.CASE_SCOPE_ATTACK);
    }

    private AgentRunLauncher launcherFor(AgentRun run, AgentSimulationClient client) {
        return new AgentRunLauncher(new RecordingOutcomes(run), client, Runnable::run);
    }

    private AgentRunCreated created(AgentSimulationScenario scenario) {
        return new AgentRunCreated("RUN-900", "PASS-900", scenario);
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

    private static final class RecordingClient implements AgentSimulationClient {

        private final RuntimeException failure;
        private String agentRunId;
        private String passportId;
        private AgentSimulationScenario scenario;

        private RecordingClient(RuntimeException failure) {
            this.failure = failure;
        }

        @Override
        public void simulate(
                String agentRunId, String passportId, AgentSimulationScenario scenario) {
            this.agentRunId = agentRunId;
            this.passportId = passportId;
            this.scenario = scenario;
            if (failure != null) {
                throw failure;
            }
        }
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
