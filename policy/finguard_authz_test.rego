package finguard.authorization_test

import rego.v1
import data.finguard.authorization

base_input := {
    "scopeStatus": {
        "employeeAuthority": "OK",
        "permissionTemplate": "OK",
        "caseStatus": "OK",
        "mandate": "OK",
        "passportStatus": "OK",
        "agentBinding": "OK",
        "customerScope": "OK",
        "toolScope": "OK",
        "dataScope": "OK",
    },
    "risk": {
        "promptInjectionDetected": false,
        "behaviorRiskLevel": "LOW",
    },
    "limits": {"hardRequestLimitExceeded": false},
}

request_with_scope_status(scope_status) := object.union(
    object.remove(base_input, {"scopeStatus"}),
    {"scopeStatus": scope_status},
)

test_all_valid_is_allowed if {
    authorization.decision with input as base_input
    authorization.decision.decision == "ALLOW" with input as base_input
}

test_case_scope_violation_is_blocked if {
    request := object.union(base_input, {
        "scopeStatus": object.union(base_input.scopeStatus, {"customerScope": "VIOLATION"}),
    })
    result := authorization.decision with input as request
    result.decision == "BLOCK"
    "CASE_SCOPE_VIOLATION" in result.reasonCodes
}

test_behavior_critical_blocks_with_valid_scope if {
    request := object.union(base_input, {
        "risk": object.union(base_input.risk, {"behaviorRiskLevel": "CRITICAL"}),
    })
    result := authorization.decision with input as request
    result.decision == "BLOCK"
    result.reasonCodes == ["BEHAVIOR_ANOMALY"]
}

test_behavior_alert_is_allowed_and_flagged if {
    request := object.union(base_input, {
        "risk": object.union(base_input.risk, {"behaviorRiskLevel": "ALERT"}),
    })
    result := authorization.decision with input as request
    result.decision == "ALLOW"
    result.riskFlagged
}

test_missing_scope_key_is_blocked if {
    malformed_scope := object.remove(base_input.scopeStatus, {"dataScope"})
    count(malformed_scope) == 8
    request := request_with_scope_status(malformed_scope)
    result := authorization.decision with input as request
    result.decision == "BLOCK"
    result.reasonCodes == ["CONTEXT_NOT_FOUND"]
}

test_typo_scope_key_cannot_replace_required_key if {
    scope_without_data := object.remove(base_input.scopeStatus, {"dataScope"})
    malformed_scope := object.union(scope_without_data, {"dataScpoe": "OK"})
    count(malformed_scope) == 9
    request := request_with_scope_status(malformed_scope)
    result := authorization.decision with input as request
    result.decision == "BLOCK"
    result.reasonCodes == ["CONTEXT_NOT_FOUND"]
}

test_extra_scope_key_is_blocked if {
    malformed_scope := object.union(base_input.scopeStatus, {"unexpectedScope": "OK"})
    count(malformed_scope) == 10
    request := request_with_scope_status(malformed_scope)
    result := authorization.decision with input as request
    result.decision == "BLOCK"
    result.reasonCodes == ["CONTEXT_NOT_FOUND"]
}

test_unknown_scope_value_is_blocked if {
    malformed_scope := object.union(base_input.scopeStatus, {"dataScope": "UNKNOWN"})
    request := request_with_scope_status(malformed_scope)
    result := authorization.decision with input as request
    result.decision == "BLOCK"
    result.reasonCodes == ["CONTEXT_NOT_FOUND"]
}
