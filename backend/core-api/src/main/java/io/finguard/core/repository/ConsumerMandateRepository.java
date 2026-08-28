package io.finguard.core.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import io.finguard.core.domain.ConsumerMandate;
import io.finguard.core.domain.TaskType;

public interface ConsumerMandateRepository extends JpaRepository<ConsumerMandate, Long> {

    /** (consumerId, purpose)는 DB 제약으로 유일하다. F02. */
    Optional<ConsumerMandate> findByConsumerIdAndPurpose(String consumerId, TaskType purpose);
}
