package io.finguard.core.risk;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.finguard.core.domain.PromptAttackType;
import io.finguard.core.domain.PromptRiskLevel;

/**
 * 검증을 통과한 Prompt Risk 평가. {@code docs/04-api-contract.md} §8.
 *
 * <p>ai-risk 응답을 그대로 믿지 않는다. 넷 중 하나라도 어긋나면 {@link #from} 이 비어서 돌아오고
 * 호출부는 스냅샷을 {@code NOT_EVALUATED} 로 남긴다. 반쯤 맞는 판정을 "검사했음"으로 기록하면
 * Audit 이 거짓이 된다 — {@code docs/04} §7.
 */
public record PromptRiskEvaluation(
        boolean detected,
        BigDecimal promptRisk,
        PromptRiskLevel riskLevel,
        PromptAttackType attackType,
        Set<String> matchedRules,
        String modelVersion) {

    /** 역직렬화 그대로의 응답. 어떤 필드도 신뢰하지 않으므로 전부 nullable 이다. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Raw(
            Boolean detected,
            BigDecimal promptRisk,
            String riskLevel,
            String attackType,
            List<String> matchedRules,
            String inputHash,
            String modelVersion) {
    }

    public static Optional<PromptRiskEvaluation> from(Raw raw, String expectedInputHash) {
        if (raw == null
                || raw.detected() == null
                || raw.promptRisk() == null
                || raw.riskLevel() == null
                || raw.modelVersion() == null
                || raw.inputHash() == null) {
            return Optional.empty();
        }
        // ContextResolveService 가 CURRENT_VERSION 으로 스냅샷을 찾는다. 다른 버전은 저장해도 보이지 않는다.
        if (!PromptRiskModel.CURRENT_VERSION.equals(raw.modelVersion())) {
            return Optional.empty();
        }
        if (!expectedInputHash.equals(raw.inputHash())) {
            return Optional.empty();
        }
        if (raw.promptRisk().compareTo(BigDecimal.ZERO) < 0
                || raw.promptRisk().compareTo(BigDecimal.ONE) > 0) {
            return Optional.empty();
        }
        PromptRiskLevel level;
        PromptAttackType attack;
        try {
            level = PromptRiskLevel.valueOf(raw.riskLevel());
            attack = raw.attackType() == null ? null : PromptAttackType.valueOf(raw.attackType());
        } catch (IllegalArgumentException unknownVocabulary) {
            return Optional.empty();
        }
        // docs/04:391 — detected 는 riskLevel == CRITICAL 과 정확히 같은 뜻이다.
        if (raw.detected() != (level == PromptRiskLevel.CRITICAL)) {
            return Optional.empty();
        }
        Set<String> rules = raw.matchedRules() == null ? Set.of() : Set.copyOf(raw.matchedRules());
        return Optional.of(
                new PromptRiskEvaluation(
                        raw.detected(),
                        raw.promptRisk().setScale(4, RoundingMode.HALF_UP),
                        level,
                        attack,
                        rules,
                        raw.modelVersion()));
    }
}
