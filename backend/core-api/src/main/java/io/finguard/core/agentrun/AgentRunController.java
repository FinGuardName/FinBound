package io.finguard.core.agentrun;

import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import io.finguard.core.api.NotImplementedResponse;

/**
 * AgentRun 생성과 Task Passport 발급 — docs/04-api-contract.md §3.
 *
 * <p>Core가 AgentRun과 TaskPassport를 소유한다. 구현은 이슈 #19.
 */
@RestController
public class AgentRunController {

    @PostMapping("/api/v1/agent-runs")
    public ResponseEntity<ProblemDetail> create() {
        return NotImplementedResponse.forEndpoint("POST /api/v1/agent-runs");
    }
}
