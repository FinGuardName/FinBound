package io.finguard.core.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import io.finguard.core.domain.AuditEvent;
import io.finguard.core.domain.AuditStatus;
import io.finguard.core.domain.PolicyDecision;

public interface AuditEventRepository
        extends JpaRepository<AuditEvent, String>, JpaSpecificationExecutor<AuditEvent> {

    Optional<AuditEvent> findByRequestId(String requestId);

    long countByStatus(AuditStatus status);

    /** ERROR는 판정을 덮으므로 ALLOW·BLOCK 집계에서 빼고 센다 — docs/06 §12. */
    long countByStatusNotAndDecision(AuditStatus status, PolicyDecision decision);

    boolean existsByRequestId(String requestId);

    List<AuditEvent> findByAgentIdAndStatusAndRequestedAtGreaterThanEqualOrderByRequestedAtDesc(
            String agentId, AuditStatus status, Instant requestedAt);
}
