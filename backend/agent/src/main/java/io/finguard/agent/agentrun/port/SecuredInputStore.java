package io.finguard.agent.agentrun.port;

import io.finguard.agent.agentrun.domain.SecuredInputReference;

public interface SecuredInputStore {
    SecuredInputReference store(String inputText);
}
