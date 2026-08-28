package io.finguard.gateway.dto;

public record ScopeStatus(
    String employeeAuthority,
    String permissionTemplate,
    String caseStatus,
    String mandate,
    String passportStatus,
    String agentBinding,
    String customerScope,
    String toolScope,
    String dataScope
) {
    public static ScopeStatus allOk() {
        return new ScopeStatus("OK", "OK", "OK", "OK", "OK", "OK", "OK", "OK", "OK");
    }
}
