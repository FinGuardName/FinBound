package io.finguard.core.audit;

import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import io.finguard.core.api.NotImplementedResponse;

/**
 * Business Audit — docs/04-api-contract.md §11.
 *
 * <p>인증 성공 이후에만 생성한다. 구현은 이슈 #21.
 */
@RestController
public class AuditController {

    @PostMapping("/internal/v1/audits")
    public ResponseEntity<ProblemDetail> create() {
        return NotImplementedResponse.forEndpoint("POST /internal/v1/audits");
    }

    @PatchMapping("/internal/v1/audits/{requestId}/outcome")
    public ResponseEntity<ProblemDetail> updateOutcome(@PathVariable String requestId) {
        return NotImplementedResponse.forEndpoint("PATCH /internal/v1/audits/" + requestId + "/outcome");
    }
}
