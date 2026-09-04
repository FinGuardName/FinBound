package io.finguard.core.agentrun;

import java.util.Optional;

import org.springframework.stereotype.Component;

import io.finguard.core.domain.PromptRiskSnapshot;
import io.finguard.core.repository.PromptRiskSnapshotRepository;
import io.finguard.core.risk.PromptRiskClient;
import io.finguard.core.risk.PromptRiskEvaluation;
import io.finguard.core.risk.PromptRiskModel;
import io.finguard.core.risk.RequestTrace;

/**
 * AgentRun 을 만들기 전에 식별자를 확보하고 Prompt Risk 를 평가한다.
 *
 * <p><strong>별도 빈이어야 한다.</strong> {@link AgentRunService#start} 는 {@code @Transactional}
 * 이고, 같은 클래스 안에서 부르면 Spring 프록시를 거치지 않아 "트랜잭션 밖에서 부른다" 가
 * 성립하지 않는다. 느린 HTTP 가 DB 커넥션을 붙잡으면 실행기가 포화되고, 포화는 거절 경로를
 * 부른다 — {@code AgentRunLauncher} 가 Agent 호출을 트랜잭션 밖으로 뺀 것과 같은 이유다.
 */
@Component
public class AgentRunPreparer {

    private final PromptRiskClient promptRisk;
    private final PromptRiskSnapshotRepository snapshots;

    public AgentRunPreparer(PromptRiskClient promptRisk, PromptRiskSnapshotRepository snapshots) {
        this.promptRisk = promptRisk;
        this.snapshots = snapshots;
    }

    public PreparedAgentRun prepare(String inputText, RequestTrace trace) {
        String agentRunId = Identifiers.agentRunId();
        String inputRef = Identifiers.inputRef();
        String inputHash = Identifiers.inputHash(inputText);

        // 이미 평가된 스냅샷이 있으면 재추론하지 않는다 — docs/06 §24.2.
        // NOT_EVALUATED 만 있으면 다시 부른다. 장애 한 번이 그 입력을 영구히 오염시키면 안 된다.
        boolean alreadyEvaluated =
                snapshots
                        .findByInputHashAndModelVersion(inputHash, PromptRiskModel.CURRENT_VERSION)
                        .filter(PromptRiskSnapshot::isEvaluated)
                        .isPresent();

        Optional<PromptRiskEvaluation> evaluation =
                alreadyEvaluated
                        ? Optional.empty()
                        : promptRisk.evaluate(agentRunId, inputRef, inputText, inputHash, trace);

        return new PreparedAgentRun(agentRunId, inputRef, inputHash, evaluation);
    }
}
