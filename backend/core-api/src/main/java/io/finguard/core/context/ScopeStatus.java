package io.finguard.core.context;

/** 성공한 Context 조회가 항상 모두 채워 반환하는 9개 Scope 상태. */
public record ScopeStatus(
        ScopeState employeeAuthority,
        ScopeState permissionTemplate,
        ScopeState caseStatus,
        ScopeState mandate,
        ScopeState passportStatus,
        ScopeState agentBinding,
        ScopeState customerScope,
        ScopeState toolScope,
        ScopeState dataScope) {
}
