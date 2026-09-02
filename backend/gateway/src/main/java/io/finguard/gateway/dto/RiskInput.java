package io.finguard.gateway.dto;

public record RiskInput(
    double promptRisk,                  // 프롬프트 인젝션 위험도 (0.0~1.0)
    String promptRiskLevel,             // LOW / ALERT / CRITICAL
    boolean promptInjectionDetected,    // 하위 호환 필드: CRITICAL과 정확히 같은 뜻
    double behaviorRisk,                // 행동 이상 점수
    String behaviorRiskLevel,           // LOW / ALERT / CRITICAL
    boolean behaviorAnomalyDetected     // 이상행동 감지됐나
) {
    public static RiskInput combine(
        PromptRiskInput prompt,
        BehaviorRiskInput behavior
    ) {
        return new RiskInput(
            prompt.promptRisk(),
            prompt.promptRiskLevel(),
            prompt.promptInjectionDetected(),
            behavior.behaviorRisk(),
            behavior.behaviorRiskLevel(),
            behavior.behaviorAnomalyDetected()
        );
    }
}
