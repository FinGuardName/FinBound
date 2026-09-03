package io.finguard.gateway.enforcement;

import org.springframework.http.HttpStatus;

import io.finguard.gateway.dto.ToolCallResponse;

public record EnforcementResult(HttpStatus status, ToolCallResponse body) {
}
