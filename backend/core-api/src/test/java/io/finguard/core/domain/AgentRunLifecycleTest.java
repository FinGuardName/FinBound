package io.finguard.core.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * AgentRun의 상태 전이. {@code docs/01-feature-spec.md} F06.
 *
 * <p>지금까지 {@code RUNNING}으로 만들고 끝이라 한 번 생긴 실행이 영원히 진행 중으로 남았다.
 * 실행이 끝났다는 사실을 기록하지 못하면 화면도 감사도 "아직 돌고 있다"고 말한다.
 */
class AgentRunLifecycleTest {

    private static final Instant STARTED = Instant.parse("2026-08-25T10:00:00Z");

    @Test
    void runningExecutionCompletes() {
        AgentRun run = running();

        run.complete();

        assertThat(run.getStatus()).isEqualTo(AgentRunStatus.COMPLETED);
    }

    @Test
    void runningExecutionFails() {
        AgentRun run = running();

        run.fail();

        assertThat(run.getStatus()).isEqualTo(AgentRunStatus.FAILED);
    }

    @Test
    void finishedExecutionDoesNotChangeAgain() {
        AgentRun run = running();
        run.complete();

        // 끝난 실행의 결론이 나중에 뒤집히면, 그 사이에 내려진 판단들의 근거가 사라진다.
        assertThatThrownBy(run::fail).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(run::complete).isInstanceOf(IllegalStateException.class);
        assertThat(run.getStatus()).isEqualTo(AgentRunStatus.COMPLETED);
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
}
