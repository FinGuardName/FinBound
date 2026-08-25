package io.finguard.gateway.authorization;

import io.finguard.gateway.dto.HardLimits;
import io.finguard.gateway.dto.RiskInput;
import io.finguard.gateway.dto.ScopeStatus;

public record AuthorizationContext(
    ScopeStatus scopeStatus,
    RiskInput risk,
    HardLimits limits
) {
}
