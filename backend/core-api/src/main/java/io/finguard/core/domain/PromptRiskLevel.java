package io.finguard.core.domain;

/** Prompt Injection 판단 등급. {@code detected=true}는 {@link #CRITICAL}과 같은 뜻이다. */
public enum PromptRiskLevel {
    LOW,
    ALERT,
    CRITICAL
}
