package io.finguard.core.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import io.finguard.core.domain.AuditEvent;
import io.finguard.core.domain.AuditStatus;

public interface AuditEventRepository extends JpaRepository<AuditEvent, String> {

    Optional<AuditEvent> findByRequestId(String requestId);

    boolean existsByRequestId(String requestId);

    List<AuditEvent> findByAgentIdAndStatusAndRequestedAtGreaterThanEqualOrderByRequestedAtDesc(
            String agentId, AuditStatus status, Instant requestedAt);
}
