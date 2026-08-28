package io.finguard.core.context;

import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import io.finguard.core.api.NotImplementedResponse;

/**
 * Runtime Financial Context Resolver — docs/04-api-contract.md §7.
 *
 * <p>Scope 비교의 Single Source of Truth. 구현은 이슈 #20.
 */
@RestController
public class ContextResolveController {

    @PostMapping("/internal/v1/context/resolve")
    public ResponseEntity<ProblemDetail> resolve() {
        return NotImplementedResponse.forEndpoint("POST /internal/v1/context/resolve");
    }
}
