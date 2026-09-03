package io.finguard.gateway.dto;

public record BehaviorRiskResult(
    double behaviorRisk,
    String behaviorRiskLevel,
    boolean isAnomaly,
    double rawScore,
    String historyStatus,
    String featureVersion,
    String modelVersion
) {
}
