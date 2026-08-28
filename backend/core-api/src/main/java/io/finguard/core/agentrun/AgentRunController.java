package io.finguard.core.agentrun;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/**
 * AgentRun 생성과 Task Passport 발급 — {@code docs/04-api-contract.md} §3.
 *
 * <p>Core가 AgentRun과 TaskPassport를 소유한다({@code docs/02-architecture.md} §7.1).
 * Agent 측은 이 엔드포인트를 호출한다.
 */
@RestController
public class AgentRunController {

    private final AgentRunService agentRunService;

    public AgentRunController(AgentRunService agentRunService) {
        this.agentRunService = agentRunService;
    }

    @PostMapping("/api/v1/agent-runs")
    public ResponseEntity<AgentRunResponse> create(@Valid @RequestBody AgentRunCreateRequest request) {
        AgentRunStarted started =
                agentRunService.start(
                        request.employeeId(), request.consumerId(), request.taskType(), request.inputText());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        new AgentRunResponse(
                                started.agentRunId(),
                                started.agentId(),
                                started.employeeId(),
                                started.caseId(),
                                started.passportId(),
                                started.inputRefs(),
                                started.status(),
                                started.startedAt()));
    }
}
