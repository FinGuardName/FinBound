package io.finguard.gateway.enforcement;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

import io.finguard.gateway.authorization.AuthorizationService;
import io.finguard.gateway.authorization.PolicyDecisionResult;
import io.finguard.gateway.dto.ToolCallRequest;
import io.finguard.gateway.dto.ToolCallResponse;
import io.finguard.gateway.filter.RequestIdFilter;
import io.finguard.gateway.identity.VerifiedAgentIdentity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * Runtime Tool Call 진입점.
 * ALLOW시 Downstream 프록시는 Phase 2에서 추가한다. Phase 1은 결정 결과만 반환한다.
 */
@RestController
@RequestMapping("/gateway/v1")
@RequiredArgsConstructor
public class ToolCallController {

    private final AuthorizationService authorizationService;

    @PostMapping("/tool-calls")
    public ResponseEntity<ToolCallResponse> handle(@Valid @RequestBody ToolCallRequest request,
                                                   HttpServletRequest http) {
        String requestId = (String) http.getAttribute(RequestIdFilter.ATTRIBUTE_REQUEST_ID);
        VerifiedAgentIdentity identity = (VerifiedAgentIdentity)
            http.getAttribute(VerifiedAgentIdentity.ATTRIBUTE_KEY);

        PolicyDecisionResult decision = authorizationService.decide(identity, request, requestId);

        if (decision.isAllow()) {
            return ResponseEntity.ok(
                ToolCallResponse.allow(requestId, Map.of("tool", request.tool())));
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ToolCallResponse.block(requestId, decision.reasonCodes()));
    }
}
