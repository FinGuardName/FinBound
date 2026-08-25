package io.finguard.core.history;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Behavior History 조회. {@code docs/04-api-contract.md} §9.
 *
 * <p>완료된 이벤트만 돌려준다. Gateway와 FastAPI는 이 API를 거치고 DB를 직접 조회하지 않는다.
 */
@RestController
public class BehaviorHistoryController {

    private final BehaviorHistoryService service;

    public BehaviorHistoryController(BehaviorHistoryService service) {
        this.service = service;
    }

    @GetMapping("/internal/v1/agents/{agentId}/behavior-history")
    public ResponseEntity<BehaviorHistoryResponse> history(
            @PathVariable String agentId, @RequestParam(defaultValue = "5m") String window) {
        return ResponseEntity.ok(service.findCompletedEvents(agentId, window));
    }
}
