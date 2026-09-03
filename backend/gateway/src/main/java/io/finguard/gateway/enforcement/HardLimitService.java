package io.finguard.gateway.enforcement;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;

@Service
public class HardLimitService {

    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
        .expireAfterAccess(Duration.ofMinutes(5))
        .maximumSize(10_000)
        .build();

    private final long capacityPerMinute;

    public HardLimitService(@Value("${finguard.hard-limit.capacity-per-minute}") long capacityPerMinute) {
        this.capacityPerMinute = capacityPerMinute;
    }

    public boolean isExceeded(String agentId) {
        Bucket bucket = buckets.get(agentId, ignored -> newBucket());
        return !bucket.tryConsume(1);
    }

    private Bucket newBucket() {
        Bandwidth limit = Bandwidth.classic(capacityPerMinute, Refill.greedy(capacityPerMinute, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }
}
