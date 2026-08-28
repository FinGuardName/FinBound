package io.finguard.agent.agentrun.adapter;

import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import io.finguard.agent.agentrun.domain.AgentRun;
import io.finguard.agent.agentrun.domain.AgentRunStatus;
import io.finguard.agent.agentrun.domain.TaskType;
import io.finguard.agent.agentrun.port.CoreAgentRunClient;
import io.finguard.agent.agentrun.service.CreateAgentRunCommand;
import reactor.core.publisher.Mono;

/** {@code docs/04-api-contract.md} §3의 Core AgentRun 발급 API 어댑터입니다. */
@Component
public class CoreAgentRunHttpClient implements CoreAgentRunClient {
    private static final String AGENT_RUNS_PATH = "/api/v1/agent-runs";

    private final WebClient webClient;

    public CoreAgentRunHttpClient(
            WebClient.Builder webClientBuilder,
            @Value("${finguard.core-api.base-url:http://localhost:8080}") String coreApiBaseUrl
    ) {
        this(webClientBuilder.baseUrl(coreApiBaseUrl).build());
    }

    CoreAgentRunHttpClient(WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    public Mono<AgentRun> create(CreateAgentRunCommand command) {
        CoreAgentRunRequest request = new CoreAgentRunRequest(
                command.employeeId(),
                command.consumerId(),
                command.taskType(),
                command.inputText()
        );

        return webClient.post()
                .uri(AGENT_RUNS_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(CoreAgentRunResponse.class)
                .map(CoreAgentRunResponse::toDomain);
    }

    private record CoreAgentRunRequest(
            String employeeId,
            String consumerId,
            TaskType taskType,
            String inputText
    ) {
    }

    private record CoreAgentRunResponse(
            String agentRunId,
            String agentId,
            String employeeId,
            String caseId,
            String passportId,
            List<String> inputRefs,
            AgentRunStatus status,
            Instant startedAt
    ) {
        private AgentRun toDomain() {
            return new AgentRun(
                    agentRunId,
                    agentId,
                    employeeId,
                    caseId,
                    passportId,
                    inputRefs,
                    status,
                    startedAt
            );
        }
    }
}
