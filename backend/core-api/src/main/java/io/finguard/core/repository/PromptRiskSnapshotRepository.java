package io.finguard.core.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.finguard.core.domain.PromptRiskSnapshot;

public interface PromptRiskSnapshotRepository extends JpaRepository<PromptRiskSnapshot, Long> {

    /**
     * 같은 입력·같은 모델의 평가 결과를 재사용한다. {@code docs/06-common-conventions.md} §24.2.
     *
     * <p>Prompt Risk는 Tool Call마다 새로 계산하는 행동 점수가 아니라 입력 버전에 붙은 스냅샷이다.
     * DB의 {@code uk_prompt_risk_input_hash_model} 이 같은 규칙을 강제한다.
     */
    Optional<PromptRiskSnapshot> findByInputHashAndModelVersion(String inputHash, String modelVersion);

    /**
     * 자리만 잡는 {@code NOT_EVALUATED} 행을 넣는다. 이미 있으면 아무것도 하지 않는다.
     *
     * <p><strong>이 저장소에서 유일한 네이티브 쿼리다.</strong> 같은 입력으로 두 실행이 동시에
     * 시작되면 둘 다 삽입을 시도하고 {@code uk_prompt_risk_input_hash_model} 에 걸린다. 그때
     * 예외는 커밋 시점에 터지고, 진 쪽 트랜잭션의 Financial Case·Task Passport·AgentRun·
     * Secured Input 이 통째로 롤백된다. 정당한 실행이 남의 타이밍 때문에 사라지는 셈이다.
     *
     * <p>JPA {@code save} 로는 이 경합을 트랜잭션 안에서 흡수할 방법이 없다 — 위반을 잡아 재시도하면
     * 실패한 트랜잭션 밖에서 해야 하므로 Case 와 Passport 부터 다시 만들게 된다. 그래서
     * {@code ON CONFLICT DO NOTHING} 을 쓴다.
     *
     * <p>{@code flushAutomatically}·{@code clearAutomatically} 가 필요하다. 네이티브 쿼리는
     * 영속성 컨텍스트를 우회하므로, 붙이지 않으면 바로 뒤 조회가 방금 넣은 행을 보지 못한다.
     *
     * @return 삽입했으면 1, 이미 있어 건너뛰었으면 0
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value =
                    """
                    insert into prompt_risk_snapshots
                        (input_ref, input_hash, evaluation_status, detected, prompt_risk,
                         risk_level, attack_type, model_version, evaluated_at)
                    values
                        (:inputRef, :inputHash, 'NOT_EVALUATED', false, 0.0000,
                         'LOW', null, :modelVersion, :recordedAt)
                    on conflict (input_hash, model_version) do nothing
                    """,
            nativeQuery = true)
    int insertIfAbsent(
            @Param("inputRef") String inputRef,
            @Param("inputHash") String inputHash,
            @Param("modelVersion") String modelVersion,
            @Param("recordedAt") Instant recordedAt);
}
