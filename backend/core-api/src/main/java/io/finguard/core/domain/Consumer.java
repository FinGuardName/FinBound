package io.finguard.core.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 금융소비자 식별자의 기준점.
 *
 * <p>데모는 <strong>CUST-9999가 실재하는데도 차단되는 것</strong>을 보여야 한다. 없는 고객이라
 * 막히는 것과 권한 범위 밖이라 막히는 것은 전혀 다른 이야기이므로, 두 고객 모두 이 표에 있어야 한다.
 */
@Entity
@Table(name = "consumers")
public class Consumer {

    @Id
    @Column(name = "consumer_id", nullable = false, length = 64)
    private String consumerId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Consumer() {
        // JPA
    }

    public Consumer(String consumerId, Instant createdAt) {
        this.consumerId = consumerId;
        this.createdAt = createdAt;
    }

    public String getConsumerId() {
        return consumerId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
