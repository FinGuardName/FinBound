package io.finguard.gateway.dto;

public record RiskInput(
    double promptRisk,                  // 프롬프트 인젝션 위험도 (0.0~1.0)
    boolean promptInjectionDetected,    // 인젝션 감지됐나
    double behaviorRisk,                // 행동 이상 점수
    String behaviorRiskLevel,           // LOW / MEDIUM / HIGH
    boolean behaviorAnomalyDetected     // 이상행동 감지됐나
) {}
