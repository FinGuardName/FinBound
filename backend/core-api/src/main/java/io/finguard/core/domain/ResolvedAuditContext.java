package io.finguard.core.domain;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Financial Context Resolver를 거친 뒤에야 알 수 있는 감사 증거.
 * {@code contracts/audit/audit-event.schema.json}이 정의하지만 선저장 시점에는 값이 없는 항목들이다.
 *
 * <p>{@link AuditCompletion}과 짝을 이룬다. 그쪽이 "무엇으로 끝났는가"라면 이쪽은 "무엇을 보고
 * 그렇게 판단했는가"다.
 *
 * <p>behavior 쪽 등급·버전 3개는 여기 없다. AI가 Gateway에 준 값이라 Core가 알 길이 없고,
 * 실으려면 {@code docs/04-api-contract.md} §11의 Outcome 본문을 넓혀야 한다 — 계약 파일이라
 * 팀 합의 대상이다.
 */
public record ResolvedAuditContext(
        String employeeId,
        String passportId,
        Set<DataType> requestedData,
        AuditScopeStatus scopeStatus,
        BigDecimal promptRisk,
        PromptRiskEvaluationStatus promptRiskEvaluationStatus,
        String promptModelVersion) {

    public ResolvedAuditContext {
        requireText(employeeId, "employeeId");
        requireText(passportId, "passportId");
        if (requestedData == null || requestedData.isEmpty()) {
            // 스키마의 minItems: 1. FK나 check로는 걸 수 없어 여기서 막는다 —
            // 빈 목록을 허용하면 "무엇을 읽으려 했는지 모르는" 감사 기록이 남는다.
            throw new IllegalArgumentException("Resolved audit context requires requestedData");
        }
        if (scopeStatus == null) {
            throw new IllegalArgumentException("Resolved audit context requires scopeStatus");
        }
        if (promptRiskEvaluationStatus == null) {
            // 검사하지 않았음과 검사했고 음성은 반드시 구분한다(docs/04 §7).
            throw new IllegalArgumentException(
                    "Resolved audit context requires promptRiskEvaluationStatus");
        }
        if (promptRisk != null
                && (promptRisk.compareTo(BigDecimal.ZERO) < 0
                        || promptRisk.compareTo(BigDecimal.ONE) > 0)) {
            throw new IllegalArgumentException("Prompt risk must be between zero and one");
        }
        requestedData = Set.copyOf(requestedData);
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Resolved audit context requires " + name);
        }
    }
}
