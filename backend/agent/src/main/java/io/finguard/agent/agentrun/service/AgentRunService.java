package io.finguard.agent.agentrun.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.finguard.agent.agentrun.domain.AgentRun;
import io.finguard.agent.agentrun.domain.AgentRunContext;
import io.finguard.agent.agentrun.domain.SecuredInputReference;
import io.finguard.agent.agentrun.port.AgentRunContextProvider;
import io.finguard.agent.agentrun.port.AgentRunRepository;
import io.finguard.agent.agentrun.port.SecuredInputStore;

@Service
public class AgentRunService {
    private final AgentRunContextProvider contextProvider;
    private final SecuredInputStore securedInputStore;
    private final AgentRunRepository agentRunRepository;
    private final String agentId;
    private final Clock clock;

    @Autowired
    public AgentRunService(
            AgentRunContextProvider contextProvider,
            SecuredInputStore securedInputStore,
            AgentRunRepository agentRunRepository,
            @Value("${finguard.agent.id:LOAN-AGENT-01}") String agentId
    ) {
        this(
                contextProvider,
                securedInputStore,
                agentRunRepository,
                agentId,
                Clock.systemDefaultZone()
        );
    }

    AgentRunService(
            AgentRunContextProvider contextProvider,
            SecuredInputStore securedInputStore,
            AgentRunRepository agentRunRepository,
            String agentId,
            Clock clock
    ) {
        this.contextProvider = contextProvider;
        this.securedInputStore = securedInputStore;
        this.agentRunRepository = agentRunRepository;
        this.agentId = agentId;
        this.clock = clock;
    }

    public AgentRun create(CreateAgentRunCommand command) {
        AgentRun agentRun = AgentRun.created(
                "RUN-" + UUID.randomUUID(),
                agentId,
                command.employeeId()
        );
        agentRunRepository.save(agentRun);

        try {
            AgentRunContext context = contextProvider.prepare(
                    command.employeeId(),
                    command.consumerId(),
                    command.taskType()
            );
            SecuredInputReference inputReference = securedInputStore.store(command.inputText());
            agentRun.start(context, inputReference, OffsetDateTime.now(clock));
            return agentRunRepository.save(agentRun);
        } catch (RuntimeException exception) {
            agentRun.fail(OffsetDateTime.now(clock));
            agentRunRepository.save(agentRun);
            throw new AgentRunCreationException(exception);
        }
    }

    public AgentRun complete(String agentRunId) {
        AgentRun agentRun = findRequired(agentRunId);
        agentRun.complete(OffsetDateTime.now(clock));
        return agentRunRepository.save(agentRun);
    }

    public AgentRun fail(String agentRunId) {
        AgentRun agentRun = findRequired(agentRunId);
        agentRun.fail(OffsetDateTime.now(clock));
        return agentRunRepository.save(agentRun);
    }

    private AgentRun findRequired(String agentRunId) {
        return agentRunRepository.findById(agentRunId)
                .orElseThrow(() -> new AgentRunNotFoundException(agentRunId));
    }
}
