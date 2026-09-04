package io.finguard.core.risk;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.finguard.core.domain.PromptAttackType;
import io.finguard.core.domain.PromptRiskLevel;

/**
 * ai-risk 응답을 그대로 믿지 않는다. 하나라도 어긋나면 비어서 돌아오고 호출부는 NOT_EVALUATED로 남긴다.
 */
class PromptRiskEvaluationTest {

    private static final String HASH = "sha256:" + "a".repeat(64);

    private PromptRiskEvaluation.Raw raw(
            Boolean detected, BigDecimal risk, String level, String hash, String version) {
        return new PromptRiskEvaluation.Raw(detected, risk, level, null, List.of(), hash, version);
    }

    @Test
    void acceptsAWellFormedResponse() {
        Optional<PromptRiskEvaluation> result =
                PromptRiskEvaluation.from(
                        raw(false, new BigDecimal("0.0500"), "LOW", HASH, PromptRiskModel.CURRENT_VERSION),
                        HASH);

        assertThat(result).isPresent();
        assertThat(result.get().riskLevel()).isEqualTo(PromptRiskLevel.LOW);
        assertThat(result.get().detected()).isFalse();
    }

    @Test
    void acceptsCriticalWhenDetectedAgrees() {
        Optional<PromptRiskEvaluation> result =
                PromptRiskEvaluation.from(
                        raw(true, new BigDecimal("0.9600"), "CRITICAL", HASH, PromptRiskModel.CURRENT_VERSION),
                        HASH);

        assertThat(result).isPresent();
        assertThat(result.get().attackType()).isNull();
    }

    @Test
    void rejectsAModelVersionThisCoreDoesNotKnow() {
        // ContextResolveService가 CURRENT_VERSION으로 스냅샷을 찾는다. 다른 버전을 저장하면
        // 스냅샷이 보이지 않게 되고, CURRENT_VERSION으로 바꿔 저장하면 거짓 기록이 된다.
        assertThat(
                        PromptRiskEvaluation.from(
                                raw(false, new BigDecimal("0.05"), "LOW", HASH, "prompt-guard-99"), HASH))
                .isEmpty();
    }

    @Test
    void rejectsAHashThatDoesNotMatchTheRequest() {
        // 다른 입력의 판정을 이 입력에 붙이지 않는다.
        assertThat(
                        PromptRiskEvaluation.from(
                                raw(false, new BigDecimal("0.05"), "LOW", "sha256:" + "b".repeat(64),
                                        PromptRiskModel.CURRENT_VERSION),
                                HASH))
                .isEmpty();
    }

    @Test
    void rejectsDetectedDisagreeingWithRiskLevel() {
        // docs/04-api-contract.md:391 — detected는 riskLevel == CRITICAL 과 정확히 같은 뜻이다.
        assertThat(
                        PromptRiskEvaluation.from(
                                raw(true, new BigDecimal("0.5"), "ALERT", HASH, PromptRiskModel.CURRENT_VERSION),
                                HASH))
                .isEmpty();
        assertThat(
                        PromptRiskEvaluation.from(
                                raw(false, new BigDecimal("0.99"), "CRITICAL", HASH,
                                        PromptRiskModel.CURRENT_VERSION),
                                HASH))
                .isEmpty();
    }

    @Test
    void rejectsMissingRequiredFields() {
        assertThat(PromptRiskEvaluation.from(raw(null, new BigDecimal("0.05"), "LOW", HASH,
                        PromptRiskModel.CURRENT_VERSION), HASH))
                .isEmpty();
        assertThat(PromptRiskEvaluation.from(raw(false, null, "LOW", HASH,
                        PromptRiskModel.CURRENT_VERSION), HASH))
                .isEmpty();
        assertThat(PromptRiskEvaluation.from(raw(false, new BigDecimal("0.05"), null, HASH,
                        PromptRiskModel.CURRENT_VERSION), HASH))
                .isEmpty();
        assertThat(PromptRiskEvaluation.from(null, HASH)).isEmpty();
    }

    @Test
    void rejectsAScoreOutsideZeroToOne() {
        assertThat(PromptRiskEvaluation.from(raw(false, new BigDecimal("1.5"), "LOW", HASH,
                        PromptRiskModel.CURRENT_VERSION), HASH))
                .isEmpty();
        assertThat(PromptRiskEvaluation.from(raw(false, new BigDecimal("-0.1"), "LOW", HASH,
                        PromptRiskModel.CURRENT_VERSION), HASH))
                .isEmpty();
    }

    @Test
    void rejectsAnUnknownRiskLevelOrAttackType() {
        assertThat(PromptRiskEvaluation.from(raw(false, new BigDecimal("0.05"), "MEDIUM", HASH,
                        PromptRiskModel.CURRENT_VERSION), HASH))
                .isEmpty();
        PromptRiskEvaluation.Raw badAttack =
                new PromptRiskEvaluation.Raw(
                        true, new BigDecimal("0.96"), "CRITICAL", "NOT_A_REAL_ATTACK", List.of(),
                        HASH, PromptRiskModel.CURRENT_VERSION);
        assertThat(PromptRiskEvaluation.from(badAttack, HASH)).isEmpty();
    }

    @Test
    void keepsAttackTypeAndMatchedRulesWhenPresent() {
        // 정상 입력은 이 둘이 비어 있다. 비었다고 실패로 보면 안 된다.
        PromptRiskEvaluation.Raw detectedRaw =
                new PromptRiskEvaluation.Raw(
                        true, new BigDecimal("0.96"), "CRITICAL", "CROSS_CUSTOMER_ACCESS",
                        List.of("IGNORE_PREVIOUS_INSTRUCTION"), HASH, PromptRiskModel.CURRENT_VERSION);

        PromptRiskEvaluation result = PromptRiskEvaluation.from(detectedRaw, HASH).orElseThrow();

        assertThat(result.attackType()).isEqualTo(PromptAttackType.CROSS_CUSTOMER_ACCESS);
        assertThat(result.matchedRules()).containsExactly("IGNORE_PREVIOUS_INSTRUCTION");
    }
}
