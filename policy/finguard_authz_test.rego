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
