package io.finguard.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import io.finguard.core.domain.SecurityAuthEvent;

public interface SecurityAuthEventRepository extends JpaRepository<SecurityAuthEvent, String> {
}
