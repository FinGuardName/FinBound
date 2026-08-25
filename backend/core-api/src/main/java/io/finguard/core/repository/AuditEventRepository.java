package io.finguard.core.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import io.finguard.core.domain.AuditEvent;

public interface AuditEventRepository extends JpaRepository<AuditEvent, String> {

    Optional<AuditEvent> findByRequestId(String requestId);

    boolean existsByRequestId(String requestId);
}
