package io.finguard.core.agentrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.finguard.core.domain.PromptRiskLevel;
import io.finguard.core.domain.PromptRiskSnapshot;
import io.finguard.core.repository.PromptRiskSnapshotRepository;
import io.finguard.core.risk.PromptRiskClient;
import io.finguard.core.risk.PromptRiskEvaluation;
import io.finguard.core.risk.PromptRiskModel;

/**
 * 준비는 쓰기 트랜잭션 밖에서 일어난다 — {@code AgentRunLauncher:60} 과 같은 이유로, 느린 HTTP 가
 * DB 커넥션을 붙잡으면 안 된다.
 */
class AgentRunPreparerTest {

    private static final Instant NOW = Instant.parse("2026-09-04T00:00:00Z");
    private static final String HASH = "sha256:" + "a".repeat(64);

    private final PromptRiskClient client = mock(PromptRiskClient.class);
    private final PromptRiskSnapshotRepository snapshots = mock(PromptRiskSnapshotRepository.class);
    private final AgentRunPreparer preparer = new AgentRunPreparer(client, snapshots);

    private PromptRiskEvaluation low() {
        return new PromptRiskEvaluation(
                false, new BigDecimal("0.0500"), PromptRiskLevel.LOW, null, Set.of(),
                PromptRiskModel.CURRENT_VERSION);
    }

    @Test
    void callsTheDetectorWhenNoSnapshotExists() {
        when(snapshots.findByInputHashAndModelVersion(any(), eq(PromptRiskModel.CURRENT_VERSION)))
                .thenReturn(Optional.empty());
        when(client.evaluate(any(), any(), any(), any())).thenReturn(Optional.of(low()));

        PreparedAgentRun prepared = preparer.prepare("대출심사를 진행해줘.");

        assertThat(prepared.evaluation()).contains(low());
        assertThat(prepared.inputHash()).startsWith("sha256:");
        assertThat(prepared.agentRunId()).isNotBlank();
        assertThat(prepared.inputRef()).isNotBlank();
        // 요청 본문에 실리는 식별자는 나중에 저장될 것과 같아야 한다.
        verify(client)
                .evaluate(prepared.agentRunId(), prepared.inputRef(), "대출심사를 진행해줘.",
                        prepared.inputHash());
    }

    @Test
    void reusesAnEvaluatedSnapshotWithoutCallingTheDetector() {
        // docs/06 §24.2 — 같은 입력은 재추론하지 않는다.
        PromptRiskSnapshot evaluated =
                PromptRiskSnapshot.notEvaluated("INPUT-OLD", HASH, PromptRiskModel.CURRENT_VERSION, NOW);
        evaluated.promote(low(), NOW);
        when(snapshots.findByInputHashAndModelVersion(any(), eq(PromptRiskModel.CURRENT_VERSION)))
                .thenReturn(Optional.of(evaluated));

        PreparedAgentRun prepared = preparer.prepare("대출심사를 진행해줘.");

        assertThat(prepared.evaluation()).isEmpty();
        verify(client, never()).evaluate(any(), any(), any(), any());
    }

    @Test
    void reEvaluatesWhenOnlyANotEvaluatedSnapshotExists() {
        // 장애로 남은 NOT_EVALUATED 행이 그 입력을 영구히 오염시키면 안 된다.
        PromptRiskSnapshot stale =
                PromptRiskSnapshot.notEvaluated("INPUT-OLD", HASH, PromptRiskModel.CURRENT_VERSION, NOW);
        when(snapshots.findByInputHashAndModelVersion(any(), eq(PromptRiskModel.CURRENT_VERSION)))
                .thenReturn(Optional.of(stale));
        when(client.evaluate(any(), any(), any(), any())).thenReturn(Optional.of(low()));

        PreparedAgentRun prepared = preparer.prepare("대출심사를 진행해줘.");

        assertThat(prepared.evaluation()).contains(low());
        verify(client, times(1)).evaluate(any(), any(), any(), any());
    }

    @Test
    void survivesADetectorFailure() {
        when(snapshots.findByInputHashAndModelVersion(any(), eq(PromptRiskModel.CURRENT_VERSION)))
                .thenReturn(Optional.empty());
        when(client.evaluate(any(), any(), any(), any())).thenReturn(Optional.empty());

        PreparedAgentRun prepared = preparer.prepare("대출심사를 진행해줘.");

        assertThat(prepared.evaluation()).isEmpty();
        assertThat(prepared.agentRunId()).isNotBlank();
    }

    @Test
    void derivesTheSameHashForTheSameTextButAFreshRunIdEachTime() {
        when(snapshots.findByInputHashAndModelVersion(any(), any())).thenReturn(Optional.empty());
        when(client.evaluate(any(), any(), any(), any())).thenReturn(Optional.empty());

        PreparedAgentRun first = preparer.prepare("같은 문장");
        PreparedAgentRun second = preparer.prepare("같은 문장");

        assertThat(first.inputHash()).isEqualTo(second.inputHash());
        assertThat(first.agentRunId()).isNotEqualTo(second.agentRunId());
        assertThat(first.inputRef()).isNotEqualTo(second.inputRef());
    }
}
