package io.finguard.core.history;

import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.finguard.core.api.NotImplementedResponse;

/**
 * Behavior History 조회 — docs/04-api-contract.md §9.
 *
 * <p>완료된 이벤트만 돌려준다. Gateway와 FastAPI는 이 API를 거치고 DB를 직접 조회하지 않는다.
 * 구현은 이슈 #22.
 */
@RestController
public class BehaviorHistoryController {

    @GetMapping("/internal/v1/agents/{agentId}/behavior-history")
    public ResponseEntity<ProblemDetail> history(
            @PathVariable String agentId, @RequestParam(defaultValue = "5m") String window) {
        return NotImplementedResponse.forEndpoint(
                "GET /internal/v1/agents/" + agentId + "/behavior-history?window=" + window);
    }
}
