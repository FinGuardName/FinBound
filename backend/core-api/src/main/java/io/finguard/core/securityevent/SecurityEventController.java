package io.finguard.core.securityevent;

import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import io.finguard.core.api.NotImplementedResponse;

/**
 * 인증 실패 SecurityAuthEvent — docs/04-api-contract.md §6.
 *
 * <p>Business Audit과 분리된 최소 기록이다. Prompt / 금융 데이터 원문을 담지 않는다. 구현은 이슈 #21.
 */
@RestController
public class SecurityEventController {

    @PostMapping("/internal/v1/security-events/auth-failure")
    public ResponseEntity<ProblemDetail> recordAuthFailure() {
        return NotImplementedResponse.forEndpoint("POST /internal/v1/security-events/auth-failure");
    }
}
