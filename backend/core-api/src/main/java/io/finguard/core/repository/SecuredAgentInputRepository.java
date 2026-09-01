package io.finguard.core.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import io.finguard.core.domain.SecuredAgentInput;

public interface SecuredAgentInputRepository extends JpaRepository<SecuredAgentInput, String> {

    Optional<SecuredAgentInput> findByInputRefAndAgentRunId(String inputRef, String agentRunId);
}
