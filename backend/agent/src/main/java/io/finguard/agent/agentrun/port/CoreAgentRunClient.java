package io.finguard.agent.agentrun.port;

import io.finguard.agent.agentrun.domain.AgentRun;
import io.finguard.agent.agentrun.service.CreateAgentRunCommand;
import reactor.core.publisher.Mono;

/** Core가 소유한 AgentRun 발급 API 경계입니다. */
public interface CoreAgentRunClient {
    Mono<AgentRun> create(CreateAgentRunCommand command);
}
