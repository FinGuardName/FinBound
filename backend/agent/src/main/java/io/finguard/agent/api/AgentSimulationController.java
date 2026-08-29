package io.finguard.agent.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.finguard.agent.service.AgentSimulationService;
import jakarta.validation.Valid;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/internal/v1/agent-simulations")
public class AgentSimulationController {
    private final AgentSimulationService simulationService;

    public AgentSimulationController(AgentSimulationService simulationService) {
        this.simulationService = simulationService;
    }

    @PostMapping
    public Mono<ResponseEntity<AgentSimulationResponse>> simulate(
            @Valid @RequestBody AgentSimulationRequest request
    ) {
        return simulationService.simulate(request).map(ResponseEntity::ok);
    }
}
