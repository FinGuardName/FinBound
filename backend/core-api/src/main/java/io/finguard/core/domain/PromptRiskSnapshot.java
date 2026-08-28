package io.finguard.core.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 특정 입력 버전에 붙은 Prompt Risk 평가 결과. {@code docs/04-api-contract.md} §4.3.
 *
 * <p>Tool Call마다 새로 계산하는 행동 점수가 아니라 <strong>입력에 연결된 스냅샷</strong>이다.
 * 같은 {@code (inputHash, modelVersion)}은 재평가하지 않으므로({@code docs/06} §24.2) 그 조합을
 * DB 제약으로 유일하게 둔다.
 *
 * <p>{@code evaluationStatus}가 핵심이다. {@code detected=false} 하나로는 "검사했고 음성"과
 * "검사하지 않았음"이 구분되지 않고, 그러면 Audit 기록이 거짓이 된다
 * ({@code docs/04-api-contract.md} §7). 이번 사이클은 Detector를 호출하지 않으므로
 * {@code NOT_EVALUATED}로 남고, 대시보드에도 그대로 드러나야 한다.
 */
@Entity
@Table(
        name = "prompt_risk_snapshots",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_prompt_risk_input_hash_model",
                        columnNames = {"input_hash", "model_version"}))
public class PromptRiskSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "snapshot_id", nullable = false)
    private Long snapshotId;

    @Column(name = "input_ref", nullable = false, length = 64)
    private String inputRef;

    @Column(name = "input_hash", nullable = false, length = 128)
    private String inputHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "evaluation_status", nullable = false, length = 32)
    private PromptRiskEvaluationStatus evaluationStatus;

    @Column(name = "detected", nullable = false)
    private boolean detected;

    @Column(name = "prompt_risk", nullable = false, precision = 5, scale = 4)
    private BigDecimal promptRisk;

    @Enumerated(EnumType.STRING)
    @Column(name = "attack_type", length = 64)
    private PromptAttackType attackType;

    /** 같은 규칙이 두 번 매칭됐다고 기록할 이유가 없으므로 Set으로 둔다. */
    @ElementCollection
    @CollectionTable(
            name = "prompt_risk_matched_rules",
            joinColumns = @JoinColumn(name = "snapshot_id"))
    @Column(name = "rule_id", nullable = false, length = 128)
    private Set<String> matchedRules = new LinkedHashSet<>();

    @Column(name = "model_version", nullable = false, length = 64)
    private String modelVersion;

    @Column(name = "evaluated_at", nullable = false)
    private Instant evaluatedAt;

    protected PromptRiskSnapshot() {
        // JPA
    }

    /** Detector를 호출하지 않은 상태의 스냅샷. 이번 사이클의 기본값이다. */
    public static PromptRiskSnapshot notEvaluated(
            String inputRef, String inputHash, String modelVersion, Instant recordedAt) {
        PromptRiskSnapshot snapshot = new PromptRiskSnapshot();
        snapshot.inputRef = inputRef;
        snapshot.inputHash = inputHash;
        snapshot.evaluationStatus = PromptRiskEvaluationStatus.NOT_EVALUATED;
        snapshot.detected = false;
        snapshot.promptRisk = BigDecimal.ZERO.setScale(4);
        snapshot.attackType = null;
        snapshot.modelVersion = modelVersion;
        snapshot.evaluatedAt = recordedAt;
        return snapshot;
    }

    public Long getSnapshotId() {
        return snapshotId;
    }

    public String getInputRef() {
        return inputRef;
    }

    public String getInputHash() {
        return inputHash;
    }

    public PromptRiskEvaluationStatus getEvaluationStatus() {
        return evaluationStatus;
    }

    public boolean isDetected() {
        return detected;
    }

    public BigDecimal getPromptRisk() {
        return promptRisk;
    }

    public PromptAttackType getAttackType() {
        return attackType;
    }

    public Set<String> getMatchedRules() {
        return Collections.unmodifiableSet(matchedRules);
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public Instant getEvaluatedAt() {
        return evaluatedAt;
    }
}
