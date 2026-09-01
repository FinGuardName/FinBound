package io.finguard.core.domain;

import java.util.Collections;
import java.util.EnumSet;
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
import jakarta.persistence.Version;

/**
 * 소비자가 특정 목적에 대해 허용한 Data 범위. {@code docs/01-feature-spec.md} F02.
 *
 * <p>{@code (consumerId, purpose)} 조합으로 유일하다 — F02가 "현재 Case의 consumerId + purpose와
 * 일치해야 한다"고 규정하므로, 같은 조합이 둘이면 어느 쪽을 볼지 정할 수 없다. DB 제약으로 막는다.
 *
 * <p>P0에서는 Seed로만 넣고 읽기 전용으로 쓴다. 수정·철회 UI는 P1이다.
 */
@Entity
@Table(
        name = "consumer_mandates",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_consumer_mandate_consumer_purpose",
                        columnNames = {"consumer_id", "purpose"}))
public class ConsumerMandate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "mandate_id", nullable = false)
    private Long mandateId;

    @Column(name = "consumer_id", nullable = false, length = 64)
    private String consumerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 64)
    private TaskType purpose;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ConsumerMandateStatus status;

    @ElementCollection
    @CollectionTable(
            name = "consumer_mandate_allowed_data",
            joinColumns = @JoinColumn(name = "mandate_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "data_type", nullable = false, length = 64)
    private Set<DataType> allowedData = EnumSet.noneOf(DataType.class);

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected ConsumerMandate() {
        // JPA
    }

    public ConsumerMandate(
            String consumerId,
            TaskType purpose,
            ConsumerMandateStatus status,
            Set<DataType> allowedData) {
        this.consumerId = consumerId;
        this.purpose = purpose;
        this.status = status;
        this.allowedData = EnumSet.noneOf(DataType.class);
        this.allowedData.addAll(allowedData);
    }

    public Long getMandateId() {
        return mandateId;
    }

    public String getConsumerId() {
        return consumerId;
    }

    public TaskType getPurpose() {
        return purpose;
    }

    public ConsumerMandateStatus getStatus() {
        return status;
    }

    public Set<DataType> getAllowedData() {
        return Collections.unmodifiableSet(allowedData);
    }

    public long getVersion() {
        return version;
    }

    public boolean isActive() {
        return status == ConsumerMandateStatus.ACTIVE;
    }
}
