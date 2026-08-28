package io.finguard.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import io.finguard.core.domain.TaskPassport;

public interface TaskPassportRepository extends JpaRepository<TaskPassport, String> {
}
