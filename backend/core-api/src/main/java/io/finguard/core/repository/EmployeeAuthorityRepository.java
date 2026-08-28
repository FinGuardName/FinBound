package io.finguard.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import io.finguard.core.domain.EmployeeAuthority;

public interface EmployeeAuthorityRepository extends JpaRepository<EmployeeAuthority, String> {
}
