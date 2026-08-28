package io.finguard.agent.agentrun.service;

import org.springframework.stereotype.Service;

import io.finguard.agent.agentrun.domain.AgentRun;
import io.finguard.agent.agentrun.port.CoreAgentRunClient;
import reactor.core.publisher.Mono;

@Service
public class AgentRunService {
    private final CoreAgentRunClient coreAgentRunClient;

    public AgentRunService(CoreAgentRunClient coreAgentRunClient) {
        this.coreAgentRunClient = coreAgentRunClient;
    }

    /**
     * Core에 AgentRun 발급을 요청합니다. Agent 모듈은 Case, Passport, input reference 또는
     * AgentRun 식별자를 만들거나 저장하지 않습니다.
     */
    public Mono<AgentRun> create(CreateAgentRunCommand command) {
        return coreAgentRunClient.create(command)
                .onErrorMap(
                        exception -> !(exception instanceof AgentRunCreationException),
                        AgentRunCreationException::new
                );
    }
}
