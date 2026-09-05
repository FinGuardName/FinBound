package io.finguard.core.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.finguard.core.domain.PromptRiskSnapshot;
import jakarta.persistence.LockModeType;

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
     * <p>{@code flushAutomatically} 가 <strong>반드시</strong> 필요하다. 이 표의
     * {@code fk_prompt_risk_input} 이 같은 트랜잭션에서 방금 만든 {@code secured_agent_inputs}
     * 행을 가리키므로, flush 하지 않으면 네이티브 INSERT 가 FK 위반으로 죽는다.
     *
     * <p>{@code clearAutomatically} 는 쓰지 않는다. 뒤따르는 조회는 DB 로 나가므로 영속성 컨텍스트를
     * 비우지 않아도 방금 넣은 행을 본다. 비우면 앞서 저장한 Case·Passport·AgentRun 이 함께
     * 분리될 뿐이다.
     *
     * @return 삽입했으면 1, 이미 있어 건너뛰었으면 0
     */
    @Modifying(flushAutomatically = true)
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

    /**
     * 승격 대상 행을 잠그고 읽는다.
     *
     * <p>{@code PromptRiskSnapshot#promote} 의 "이미 EVALUATED 면 거절" 검사만으로는 부족하다.
     * {@code NOT_EVALUATED} 행이 이미 있을 때 두 실행이 동시에 들어오면 둘 다 그 상태를 읽고
     * 둘 다 UPDATE 를 날려 나중 커밋이 이긴다. 엔티티에 {@code @Version} 이 없어 낙관적 잠금도
     * 걸리지 않는다.
     *
     * <p>행 잠금으로 직렬화한다. 뒤에 온 쪽은 앞이 커밋한 뒤 잠금을 얻고, 그때 다시 읽은 상태가
     * {@code EVALUATED} 라 {@code promote} 가 정상적으로 거절한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from PromptRiskSnapshot s where s.inputHash = :inputHash"
            + " and s.modelVersion = :modelVersion")
    Optional<PromptRiskSnapshot> lockForPromotion(
            @Param("inputHash") String inputHash, @Param("modelVersion") String modelVersion);
}
