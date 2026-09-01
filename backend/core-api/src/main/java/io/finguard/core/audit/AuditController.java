package io.finguard.core.audit;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/**
 * Business Audit 영속화 API. {@code docs/04-api-contract.md} §11.
 */
@RestController
public class AuditController {

    private static final String VERIFIED_AGENT_HEADER = "X-Verified-Agent-Id";

    private final AuditService service;

    public AuditController(AuditService service) {
        this.service = service;
    }

    @PostMapping("/internal/v1/audits")
    public ResponseEntity<AuditResponse> create(
            @RequestHeader(VERIFIED_AGENT_HEADER) String verifiedAgentId,
            @Valid @RequestBody AuditCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(request, verifiedAgentId));
    }

    @PatchMapping("/internal/v1/audits/{requestId}/outcome")
    public ResponseEntity<AuditResponse> updateOutcome(
            @PathVariable String requestId,
            @RequestHeader(VERIFIED_AGENT_HEADER) String verifiedAgentId,
            @Valid @RequestBody AuditOutcomeRequest request) {
        return ResponseEntity.ok(service.updateOutcome(requestId, request, verifiedAgentId));
    }
}
