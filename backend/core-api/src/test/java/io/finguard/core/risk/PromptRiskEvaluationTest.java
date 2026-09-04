package io.finguard.core.risk;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
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
    private static final Instant AT = Instant.parse("2026-09-04T00:00:00Z");

    private PromptRiskEvaluation.Raw raw(
            Boolean detected, BigDecimal risk, String level, String hash, String version) {
        return new PromptRiskEvaluation.Raw(detected, risk, level, null, List.of(), hash, version, AT);
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
                        HASH, PromptRiskModel.CURRENT_VERSION, AT);
        assertThat(PromptRiskEvaluation.from(badAttack, HASH)).isEmpty();
    }

    @Test
    void rejectsAResponseWithoutAnEvaluationTime() {
        // docs/04 §8 응답은 evaluatedAt 을 포함한다. 없는데 통과시키면 Core 수신 시각으로 채우게
        // 되고, 그건 응답에 없던 평가 시각을 지어내 기록하는 것이다.
        PromptRiskEvaluation.Raw missing =
                new PromptRiskEvaluation.Raw(
                        false, new BigDecimal("0.05"), "LOW", null, List.of(), HASH,
                        PromptRiskModel.CURRENT_VERSION, null);

        assertThat(PromptRiskEvaluation.from(missing, HASH)).isEmpty();
    }

    @Test
    void keepsTheEvaluationTimeTheDetectorReported() {
        PromptRiskEvaluation result =
                PromptRiskEvaluation.from(
                                raw(false, new BigDecimal("0.05"), "LOW", HASH,
                                        PromptRiskModel.CURRENT_VERSION),
                                HASH)
                        .orElseThrow();

        assertThat(result.evaluatedAt()).isEqualTo(AT);
    }

    @Test
    void rejectsARuleIdLongerThanTheColumn() {
        // prompt_risk_matched_rules.rule_id 가 varchar(128) 이다. 통과시키면 커밋 시점에 제약
        // 위반이 터져 AgentRun 전체가 롤백된다 — "평가 실패는 치명적이지 않다" 는 약속이 깨진다.
        PromptRiskEvaluation.Raw tooLong =
                new PromptRiskEvaluation.Raw(
                        true, new BigDecimal("0.96"), "CRITICAL", null, List.of("R".repeat(129)),
                        HASH, PromptRiskModel.CURRENT_VERSION, AT);

        assertThat(PromptRiskEvaluation.from(tooLong, HASH)).isEmpty();
    }

    @Test
    void rejectsANullRuleIdWithoutThrowing() {
        PromptRiskEvaluation.Raw nullRule =
                new PromptRiskEvaluation.Raw(
                        true, new BigDecimal("0.96"), "CRITICAL", null,
                        java.util.Arrays.asList("VALID_RULE", null), HASH,
                        PromptRiskModel.CURRENT_VERSION, AT);

        assertThat(PromptRiskEvaluation.from(nullRule, HASH)).isEmpty();
    }

    @Test
    void acceptsARuleIdExactlyAtTheColumnLimit() {
        PromptRiskEvaluation.Raw atLimit =
                new PromptRiskEvaluation.Raw(
                        true, new BigDecimal("0.96"), "CRITICAL", null, List.of("R".repeat(128)),
                        HASH, PromptRiskModel.CURRENT_VERSION, AT);

        assertThat(PromptRiskEvaluation.from(atLimit, HASH)).isPresent();
    }

    @Test
    void keepsAttackTypeAndMatchedRulesWhenPresent() {
        // 정상 입력은 이 둘이 비어 있다. 비었다고 실패로 보면 안 된다.
        PromptRiskEvaluation.Raw detectedRaw =
                new PromptRiskEvaluation.Raw(
                        true, new BigDecimal("0.96"), "CRITICAL", "CROSS_CUSTOMER_ACCESS",
                        List.of("IGNORE_PREVIOUS_INSTRUCTION"), HASH, PromptRiskModel.CURRENT_VERSION, AT);

        PromptRiskEvaluation result = PromptRiskEvaluation.from(detectedRaw, HASH).orElseThrow();

        assertThat(result.attackType()).isEqualTo(PromptAttackType.CROSS_CUSTOMER_ACCESS);
        assertThat(result.matchedRules()).containsExactly("IGNORE_PREVIOUS_INSTRUCTION");
    }
}
