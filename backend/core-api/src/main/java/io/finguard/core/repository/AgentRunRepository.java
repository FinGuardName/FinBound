package io.finguard.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import io.finguard.core.domain.AgentRun;

public interface AgentRunRepository extends JpaRepository<AgentRun, String> {
}
