package io.finguard.core.risk;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.finguard.core.domain.PromptRiskEvaluationStatus;
import io.finguard.core.domain.PromptRiskLevel;
import io.finguard.core.domain.PromptRiskSnapshot;

/** 승격은 한 방향이다. EVALUATED 를 덮어쓰지 않는다. */
class PromptRiskSnapshotPromotionTest {

    private static final Instant NOW = Instant.parse("2026-09-04T00:00:00Z");
    private static final String HASH = "sha256:" + "a".repeat(64);

    private PromptRiskEvaluation critical() {
        return new PromptRiskEvaluation(
                true,
                new BigDecimal("0.9600"),
                PromptRiskLevel.CRITICAL,
                null,
                Set.of("IGNORE_PREVIOUS_INSTRUCTION"),
                PromptRiskModel.CURRENT_VERSION,
                NOW.plusSeconds(1));
    }

    private PromptRiskEvaluation low() {
        return new PromptRiskEvaluation(
                false, new BigDecimal("0.0500"), PromptRiskLevel.LOW, null, Set.of(),
                PromptRiskModel.CURRENT_VERSION, NOW);
    }

    @Test
    void promotesANotEvaluatedSnapshot() {
        PromptRiskSnapshot snapshot =
                PromptRiskSnapshot.notEvaluated("INPUT-001", HASH, PromptRiskModel.CURRENT_VERSION, NOW);

        boolean promoted = snapshot.promote(critical());

        assertThat(promoted).isTrue();
        assertThat(snapshot.getEvaluationStatus()).isEqualTo(PromptRiskEvaluationStatus.EVALUATED);
        assertThat(snapshot.isDetected()).isTrue();
        assertThat(snapshot.getRiskLevel()).isEqualTo(PromptRiskLevel.CRITICAL);
        assertThat(snapshot.getPromptRisk()).isEqualByComparingTo("0.9600");
        assertThat(snapshot.getMatchedRules()).containsExactly("IGNORE_PREVIOUS_INSTRUCTION");
        assertThat(snapshot.getEvaluatedAt()).isEqualTo(NOW.plusSeconds(1));
    }

    @Test
    void refusesToOverwriteAnAlreadyEvaluatedSnapshot() {
        // 같은 입력·같은 모델이면 결과가 같아야 하지만, 그 가정을 코드로 강제한다.
        // 재평가가 기존 판정을 조용히 바꾸면 Audit 이 사후에 달라진다.
        PromptRiskSnapshot snapshot =
                PromptRiskSnapshot.notEvaluated("INPUT-001", HASH, PromptRiskModel.CURRENT_VERSION, NOW);
        snapshot.promote(critical());

        boolean promotedAgain = snapshot.promote(low());

        assertThat(promotedAgain).isFalse();
        assertThat(snapshot.getRiskLevel()).isEqualTo(PromptRiskLevel.CRITICAL);
        assertThat(snapshot.getEvaluatedAt()).isEqualTo(NOW.plusSeconds(1));
    }

    @Test
    void reportsWhetherItHasBeenEvaluated() {
        PromptRiskSnapshot snapshot =
                PromptRiskSnapshot.notEvaluated("INPUT-001", HASH, PromptRiskModel.CURRENT_VERSION, NOW);

        assertThat(snapshot.isEvaluated()).isFalse();
        snapshot.promote(low());
        assertThat(snapshot.isEvaluated()).isTrue();
    }
}
