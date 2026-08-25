package io.finguard.agent.agentrun.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.finguard.agent.agentrun.domain.AgentRun;
import io.finguard.agent.agentrun.domain.AgentRunContext;
import io.finguard.agent.agentrun.domain.SecuredInputReference;
import io.finguard.agent.agentrun.domain.TaskType;

class AgentRunAdapterTest {
    @Test
    void storesInputBehindReferenceWithStableHash() {
        InMemorySecuredInputStore store = new InMemorySecuredInputStore();

        SecuredInputReference first = store.store("대출심사를 진행해줘.");
        SecuredInputReference second = store.store("대출심사를 진행해줘.");

        assertThat(first.inputRef()).startsWith("INPUT-").isNotEqualTo(second.inputRef());
        assertThat(first.inputHash()).startsWith("sha256:").isEqualTo(second.inputHash());
        assertThat(first.toString()).doesNotContain("대출심사를 진행해줘.");
    }

    @Test
    void storesAndFindsAgentRun() {
        InMemoryAgentRunRepository repository = new InMemoryAgentRunRepository();
        AgentRun agentRun = AgentRun.created("RUN-001", "LOAN-AGENT-01", "EMP-101");

        assertThat(repository.save(agentRun)).isSameAs(agentRun);
        assertThat(repository.findById("RUN-001")).containsSame(agentRun);
        assertThat(repository.findById("RUN-999")).isEmpty();
    }

    @Test
    void localContextUsesNonAuthoritativeReferences() {
        LocalAgentRunContextProvider provider = new LocalAgentRunContextProvider();

        AgentRunContext context = provider.prepare("EMP-101", "CUST-1001", TaskType.LOAN_REVIEW);

        assertThat(context.caseId()).startsWith("LOCAL-CASE-");
        assertThat(context.passportId()).startsWith("LOCAL-PASS-");
    }
}
