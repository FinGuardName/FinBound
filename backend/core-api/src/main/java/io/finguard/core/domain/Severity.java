package io.finguard.core.domain;

/** OPA가 정책 판정과 함께 반환하는 감사 중요도. */
public enum Severity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}
