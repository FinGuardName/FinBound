package io.finguard.core.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import io.finguard.core.domain.PromptRiskSnapshot;

public interface PromptRiskSnapshotRepository extends JpaRepository<PromptRiskSnapshot, Long> {

    /**
     * 같은 입력·같은 모델의 평가 결과를 재사용한다. {@code docs/06-common-conventions.md} §24.2.
     *
     * <p>Prompt Risk는 Tool Call마다 새로 계산하는 행동 점수가 아니라 입력 버전에 붙은 스냅샷이다.
     * DB의 {@code uk_prompt_risk_input_hash_model} 이 같은 규칙을 강제한다.
     */
    Optional<PromptRiskSnapshot> findByInputHashAndModelVersion(String inputHash, String modelVersion);
}
