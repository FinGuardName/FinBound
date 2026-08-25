package io.finguard.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import io.finguard.core.domain.SecuredAgentInput;

public interface SecuredAgentInputRepository extends JpaRepository<SecuredAgentInput, String> {
}
