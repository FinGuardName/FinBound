package io.finguard.core.risk;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.finguard.core.domain.PromptAttackType;
import io.finguard.core.domain.PromptRiskLevel;

/**
 * 검증을 통과한 Prompt Risk 평가. {@code docs/04-api-contract.md} §8.
 *
 * <p>ai-risk 응답을 그대로 믿지 않는다. 검사 중 하나라도 어긋나면 {@link #from} 이 비어서 돌아오고
 * 호출부는 스냅샷을 {@code NOT_EVALUATED} 로 남긴다. 반쯤 맞는 판정을 "검사했음"으로 기록하면
 * Audit 이 거짓이 된다 — {@code docs/04} §7.
 *
 * <p>{@code evaluatedAt} 은 <strong>ai-risk 가 보낸 값</strong>이다. Core 의 수신 시각으로 채우면
 * "언제 검사했는가" 에 실제로 추론이 일어난 시각이 아닌 값이 들어간다. 응답에 없거나 파싱되지
 * 않으면 평가 실패로 본다 — 없는 시각을 지어내지 않는다.
 */
public record PromptRiskEvaluation(
        boolean detected,
        BigDecimal promptRisk,
        PromptRiskLevel riskLevel,
        PromptAttackType attackType,
        Set<String> matchedRules,
        String modelVersion,
        Instant evaluatedAt) {

    /** {@code prompt_risk_matched_rules.rule_id} 가 {@code varchar(128)} 이다 — V1__baseline.sql. */
    private static final int MAX_RULE_ID_LENGTH = 128;

    /** 역직렬화 그대로의 응답. 어떤 필드도 신뢰하지 않으므로 전부 nullable 이다. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Raw(
            Boolean detected,
            BigDecimal promptRisk,
            String riskLevel,
            String attackType,
            List<String> matchedRules,
            String inputHash,
            String modelVersion,
            Instant evaluatedAt) {
    }

    public static Optional<PromptRiskEvaluation> from(Raw raw, String expectedInputHash) {
        if (raw == null
                || raw.detected() == null
                || raw.promptRisk() == null
                || raw.riskLevel() == null
                || raw.modelVersion() == null
                || raw.inputHash() == null
                || raw.evaluatedAt() == null) {
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
        // 컬럼보다 긴 rule id 를 통과시키면 커밋 시점에 제약 위반이 터져 AgentRun 전체가 롤백된다.
        // "평가 실패는 치명적이지 않다" 는 약속이 그 자리에서 깨진다.
        List<String> rawRules = raw.matchedRules() == null ? List.of() : raw.matchedRules();
        for (String rule : rawRules) {
            if (rule == null || rule.isBlank() || rule.length() > MAX_RULE_ID_LENGTH) {
                return Optional.empty();
            }
        }
        Set<String> rules = Set.copyOf(rawRules);
        return Optional.of(
                new PromptRiskEvaluation(
                        raw.detected(),
                        raw.promptRisk().setScale(4, RoundingMode.HALF_UP),
                        level,
                        attack,
                        rules,
                        raw.modelVersion(),
                        raw.evaluatedAt()));
    }
}
