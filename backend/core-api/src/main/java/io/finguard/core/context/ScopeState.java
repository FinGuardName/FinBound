package io.finguard.core.context;

/** Resolver가 계산하고 Rego가 그대로 소비하는 이진 Scope 상태. */
public enum ScopeState {
    OK,
    VIOLATION,
}
