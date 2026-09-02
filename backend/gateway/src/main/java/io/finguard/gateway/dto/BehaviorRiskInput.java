package io.finguard.gateway.dto;

/** 현재 Tool Call과 최근 이력으로 AI Risk Engine이 계산한 행동 위험 입력. */
public record BehaviorRiskInput(
    double behaviorRisk,
    String behaviorRiskLevel,
    boolean behaviorAnomalyDetected
) { }
