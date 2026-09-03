package io.finguard.core.agentrun;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * AgentRun이 저장된 뒤 Agent를 깨우고, 결과를 실행 상태에 반영한다.
 * {@code docs/02-architecture.md}:53-58의 {@code CORE --> AG}가 이 자리다.
 *
 * <p><strong>커밋 이후에만 부른다.</strong> Agent는 Gateway를 거쳐 Core로 되돌아온다 — 커밋 전에
 * 부르면 그 되돌아온 요청이 아직 없는 Passport를 찾는다.
 *
 * <p><strong>{@code @Async}를 쓰지 않고 실행기에 직접 제출한다.</strong> {@code @Async}를 쓰면 대기열이
 * 가득 찼을 때 제출 실패가 이 메서드 <em>바깥</em>에서 일어나고, 그 예외는 트랜잭션 완료 단계에서
 * Spring이 로그만 남기고 삼킨다. 호출자는 이미 201을 받은 뒤라 아무에게도 닿지 않는다. 그러면
 * AgentRun이 영원히 {@code RUNNING}으로 남는데, 그게 이 클래스가 없애려던 상태다.
 * 직접 제출하면 거절을 여기서 잡아 {@code FAILED}로 남길 수 있다.
 *
 * <p><strong>남은 구멍:</strong> 제출에 성공한 뒤 프로세스가 죽으면 여전히 {@code RUNNING}으로 남는다.
 * 이건 내구성 있는 outbox와 미완 실행 조정(reconciliation)이 있어야 닫히고, 이 변경의 범위를 넘는다.
 */
@Component
public class AgentRunLauncher {

    private static final Logger log = LoggerFactory.getLogger(AgentRunLauncher.class);

    private final AgentRunOutcomes outcomes;
    private final AgentSimulationClient agentSimulations;
    private final Executor executor;

    public AgentRunLauncher(
            AgentRunOutcomes outcomes,
            AgentSimulationClient agentSimulations,
            @Qualifier("agentLaunchExecutor") Executor executor) {
        this.outcomes = outcomes;
        this.agentSimulations = agentSimulations;
        this.executor = executor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAgentRunCreated(AgentRunCreated event) {
        try {
            executor.execute(() -> launch(event));
        } catch (RejectedExecutionException rejected) {
            // 부르지도 못했다. 같은 스레드에서 즉시 기록한다 — 여기서 놓치면 아무도 못 적는다.
            log.error("Cannot start the agent; the launch queue refused the work. agentRunId={}",
                    event.agentRunId(), rejected);
            outcomes.fail(event.agentRunId());
        }
    }

    /**
     * Agent 호출은 트랜잭션 밖이다. 느린 HTTP가 DB 커넥션을 붙잡으면 실행기가 포화되고,
     * 포화는 위의 거절 경로를 부른다 — 스스로 문제를 만들지 않는다.
     *
     * <p>{@link RuntimeException}을 넓게 잡는다. {@link AgentSimulationFailedException}만 잡으면
     * 다른 구현이나 프록시가 던진 예외에서 실행이 {@code RUNNING}으로 남는다.
     */
    void launch(AgentRunCreated event) {
        try {
            agentSimulations.simulate(event.agentRunId(), event.passportId(), event.scenario());
            outcomes.complete(event.agentRunId());
        } catch (RuntimeException failure) {
            log.warn("Agent simulation failed. agentRunId={}", event.agentRunId(), failure);
            outcomes.fail(event.agentRunId());
        }
    }
}
