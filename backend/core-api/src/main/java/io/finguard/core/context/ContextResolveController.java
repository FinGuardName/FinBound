package io.finguard.core.context;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/**
 * Runtime Financial Context Resolver — docs/04-api-contract.md §7.
 */
@RestController
public class ContextResolveController {

    private static final String VERIFIED_AGENT_HEADER = "X-Verified-Agent-Id";

    private final ContextResolveService service;

    public ContextResolveController(ContextResolveService service) {
        this.service = service;
    }

    @PostMapping("/internal/v1/context/resolve")
    public ResponseEntity<ContextResolveResponse> resolve(
            @RequestHeader(VERIFIED_AGENT_HEADER) String verifiedAgentId,
            @Valid @RequestBody ContextResolveRequest request) {
        // Runtime identity는 본문 값이 아니라 Gateway가 인증 후 넣은 헤더를 신뢰한다.
        return ResponseEntity.ok(service.resolve(request, verifiedAgentId));
    }
}
