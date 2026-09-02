package io.finguard.gateway.enforcement;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.finguard.gateway.dto.ToolCallRequest;
import io.finguard.gateway.dto.ToolCallResponse;
import io.finguard.gateway.filter.RequestIdFilter;
import io.finguard.gateway.identity.VerifiedAgentIdentity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/gateway/v1")
@RequiredArgsConstructor
public class ToolCallController {

    private final ToolCallEnforcementService enforcementService;

    @PostMapping("/tool-calls")
    public ResponseEntity<ToolCallResponse> handle(@Valid @RequestBody ToolCallRequest request,
                                                   HttpServletRequest http) {
        String requestId = requiredAttribute(http, RequestIdFilter.ATTRIBUTE_REQUEST_ID, "requestId");
        String traceparent = requiredAttribute(http, RequestIdFilter.ATTRIBUTE_TRACEPARENT, "traceparent");
        VerifiedAgentIdentity identity = (VerifiedAgentIdentity)
            http.getAttribute(VerifiedAgentIdentity.ATTRIBUTE_KEY);

        EnforcementResult result = enforcementService.enforce(identity, request, requestId, traceparent);
        return ResponseEntity.status(result.status()).body(result.body());
    }

    private String requiredAttribute(HttpServletRequest http, String key, String label) {
        Object value = http.getAttribute(key);
        if (!(value instanceof String stringValue) || stringValue.isBlank()) {
            throw new IllegalStateException("Missing request attribute: " + label);
        }
        return stringValue;
    }
}
