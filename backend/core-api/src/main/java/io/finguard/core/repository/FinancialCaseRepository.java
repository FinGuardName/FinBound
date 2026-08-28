package io.finguard.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import io.finguard.core.domain.FinancialCase;

public interface FinancialCaseRepository extends JpaRepository<FinancialCase, String> {
}
