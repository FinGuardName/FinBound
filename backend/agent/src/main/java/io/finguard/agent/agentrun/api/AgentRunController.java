package io.finguard.agent.agentrun.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.finguard.agent.agentrun.domain.AgentRun;
import io.finguard.agent.agentrun.service.AgentRunService;
import io.finguard.agent.agentrun.service.CreateAgentRunCommand;
import jakarta.validation.Valid;

@Validated
@RestController
@RequestMapping("/api/v1/agent-runs")
public class AgentRunController {
    private final AgentRunService agentRunService;

    public AgentRunController(AgentRunService agentRunService) {
        this.agentRunService = agentRunService;
    }

    @PostMapping
    public ResponseEntity<AgentRunResponse> create(@Valid @RequestBody AgentRunCreateRequest request) {
        AgentRun agentRun = agentRunService.create(new CreateAgentRunCommand(
                request.employeeId(),
                request.consumerId(),
                request.taskType(),
                request.inputText()
        ));
        return ResponseEntity.status(HttpStatus.CREATED).body(AgentRunResponse.from(agentRun));
    }
}
