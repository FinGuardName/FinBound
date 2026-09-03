package io.finguard.core.agentrun;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.finguard.core.domain.AgentRun;
import io.finguard.core.repository.AgentRunRepository;

/**
 * 짧은 새 트랜잭션에서 실행 상태만 기록한다.
 *
 * <p>{@link Propagation#REQUIRES_NEW}인 이유는 이 호출이 이미 커밋된 요청의 뒤에 오기 때문이다.
 * 원래 트랜잭션은 닫혀 있고 여기서 새로 열어야 한다.
 *
 * <p>로드한 엔티티는 managed 상태라 {@code complete()}·{@code fail()}의 변경이 dirty checking으로
 * 반영된다. 명시적인 {@code save()}는 필요 없다.
 */
@Component
public class TransactionalAgentRunOutcomes implements AgentRunOutcomes {

    private static final Logger log = LoggerFactory.getLogger(TransactionalAgentRunOutcomes.class);

    private final AgentRunRepository agentRuns;

    public TransactionalAgentRunOutcomes(AgentRunRepository agentRuns) {
        this.agentRuns = agentRuns;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(String agentRunId) {
        apply(agentRunId, AgentRun::complete, "complete");
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(String agentRunId) {
        apply(agentRunId, AgentRun::fail, "fail");
    }

    private void apply(String agentRunId, java.util.function.Consumer<AgentRun> transition, String what) {
        agentRuns
                .findById(agentRunId)
                .ifPresentOrElse(
                        run -> {
                            try {
                                transition.accept(run);
                            } catch (IllegalStateException alreadyFinished) {
                                // 이미 끝난 실행이다. 결론을 뒤집지 않는다 — 먼저 적힌 쪽이 사실이다.
                                log.warn(
                                        "AgentRun already finished; keeping the first outcome."
                                                + " agentRunId={} attempted={}",
                                        agentRunId,
                                        what);
                            }
                        },
                        () ->
                                log.error(
                                        "AgentRun disappeared before its outcome could be recorded."
                                                + " agentRunId={}",
                                        agentRunId));
    }
}
