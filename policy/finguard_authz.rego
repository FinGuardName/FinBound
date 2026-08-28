package finguard.authorization

import rego.v1

policy_version := "loan-review-policy-1"

scope_status_keys := {
    "employeeAuthority",
    "permissionTemplate",
    "caseStatus",
    "mandate",
    "passportStatus",
    "agentBinding",
    "customerScope",
    "toolScope",
    "dataScope",
}

scope_status_values := {"OK", "VIOLATION"}

# 키 집합이 정확히 일치해야 하고, 값도 정해진 두 가지여야 한다.
# 개수만 세면 필수 키를 빼고 오타 키를 넣어도 통과하고, "UNKNOWN" 같은 값이
# 어떤 deny 규칙과도 매치되지 않아 ALLOW로 흐른다.
valid_scope_status if {
    object.keys(input.scopeStatus) == scope_status_keys
    every status in input.scopeStatus {
        status in scope_status_values
    }
}

valid_input if {
    valid_scope_status
    input.risk.promptInjectionDetected in {true, false}
    input.risk.behaviorRiskLevel in {"LOW", "ALERT", "CRITICAL"}
    input.limits.hardRequestLimitExceeded in {true, false}
}

deny_reasons contains "CONTEXT_NOT_FOUND" if { not valid_input }
deny_reasons contains "EMPLOYEE_AUTHORITY_VIOLATION" if { input.scopeStatus.employeeAuthority == "VIOLATION" }
deny_reasons contains "PERMISSION_TEMPLATE_VIOLATION" if { input.scopeStatus.permissionTemplate == "VIOLATION" }
deny_reasons contains "CASE_INACTIVE" if { input.scopeStatus.caseStatus == "VIOLATION" }
deny_reasons contains "MANDATE_SCOPE_VIOLATION" if { input.scopeStatus.mandate == "VIOLATION" }
deny_reasons contains "TASK_PASSPORT_INACTIVE" if { input.scopeStatus.passportStatus == "VIOLATION" }
deny_reasons contains "AGENT_IDENTITY_MISMATCH" if { input.scopeStatus.agentBinding == "VIOLATION" }
deny_reasons contains "CASE_SCOPE_VIOLATION" if { input.scopeStatus.customerScope == "VIOLATION" }
deny_reasons contains "TOOL_SCOPE_VIOLATION" if { input.scopeStatus.toolScope == "VIOLATION" }
deny_reasons contains "DATA_SCOPE_VIOLATION" if { input.scopeStatus.dataScope == "VIOLATION" }
deny_reasons contains "PROMPT_INJECTION" if { input.risk.promptInjectionDetected }
deny_reasons contains "BEHAVIOR_ANOMALY" if { input.risk.behaviorRiskLevel == "CRITICAL" }
deny_reasons contains "HARD_REQUEST_LIMIT_EXCEEDED" if { input.limits.hardRequestLimitExceeded }

decision := {
    "decision": "BLOCK",
    "severity": "CRITICAL",
    "riskFlagged": true,
    "reasonCodes": sort(deny_reasons),
    "policyVersion": policy_version,
} if {
    count(deny_reasons) > 0
}

decision := {
    "decision": "ALLOW",
    "severity": severity,
    "riskFlagged": input.risk.behaviorRiskLevel == "ALERT",
    "reasonCodes": [],
    "policyVersion": policy_version,
} if {
    valid_input
    count(deny_reasons) == 0
    severity := object.get({"LOW": "LOW", "ALERT": "HIGH"}, input.risk.behaviorRiskLevel, "LOW")
}
