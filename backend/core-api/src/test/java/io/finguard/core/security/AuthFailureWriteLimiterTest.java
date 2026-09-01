package io.finguard.core.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

/**
 * 인증되지 않은 요청이 {@code security_auth_events} 를 무한히 불리지 못하게 막는다.
 *
 * <p>막는 것은 <strong>기록</strong>이지 요청 거부가 아니다. 401·403은 한도와 무관하게 언제나 나간다 —
 * 한도가 차면 문이 열리는 구조로 만들면 그게 곧 우회 수단이 된다.
 */
class AuthFailureWriteLimiterTest {

    private static final Instant START = Instant.parse("2026-08-26T09:00:00Z");
    private static final Duration WINDOW = Duration.ofMinutes(1);

    @Test
    void allowsWritesUpToTheLimitWithinOneWindow() {
        AuthFailureWriteLimiter limiter = new AuthFailureWriteLimiter(3, 10, WINDOW);

        assertThat(limiter.tryAcquire("ip-a", START).allowed()).isTrue();
        assertThat(limiter.tryAcquire("ip-a", START).allowed()).isTrue();
        assertThat(limiter.tryAcquire("ip-a", START).allowed()).isTrue();
    }

    @Test
    void refusesFurtherWritesOnceTheLimitIsReached() {
        AuthFailureWriteLimiter limiter = new AuthFailureWriteLimiter(2, 10, WINDOW);
        limiter.tryAcquire("ip-a", START);
        limiter.tryAcquire("ip-a", START);

        assertThat(limiter.tryAcquire("ip-a", START).allowed()).isFalse();
    }

    /** 한 곳에서 오는 폭주가 다른 곳의 시도를 기록에서 가리면 안 된다. */
    @Test
    void keepsOneSourcesFloodFromConsumingAnothersAllowance() {
        AuthFailureWriteLimiter limiter = new AuthFailureWriteLimiter(1, 10, WINDOW);
        limiter.tryAcquire("ip-a", START);
        limiter.tryAcquire("ip-a", START);

        assertThat(limiter.tryAcquire("ip-b", START).allowed()).isTrue();
    }

    @Test
    void startsAFreshAllowanceInTheNextWindow() {
        AuthFailureWriteLimiter limiter = new AuthFailureWriteLimiter(1, 10, WINDOW);
        limiter.tryAcquire("ip-a", START);

        assertThat(limiter.tryAcquire("ip-a", START.plus(WINDOW)).allowed()).isTrue();
    }

    /** 버려진 건수를 세어 두지 않으면 "조용했다"와 "폭주를 버렸다"가 구분되지 않는다. */
    @Test
    void reportsHowManyWritesTheClosedWindowDropped() {
        AuthFailureWriteLimiter limiter = new AuthFailureWriteLimiter(1, 10, WINDOW);
        limiter.tryAcquire("ip-a", START);
        limiter.tryAcquire("ip-a", START);
        limiter.tryAcquire("ip-a", START);

        assertThat(limiter.tryAcquire("ip-a", START.plus(WINDOW)).droppedInClosedWindow()).isEqualTo(2);
    }

    @Test
    void reportsNothingDroppedWhenTheClosedWindowWasWithinTheLimit() {
        AuthFailureWriteLimiter limiter = new AuthFailureWriteLimiter(5, 10, WINDOW);
        limiter.tryAcquire("ip-a", START);

        assertThat(limiter.tryAcquire("ip-a", START.plus(WINDOW)).droppedInClosedWindow()).isZero();
    }

    /**
     * 출처를 바꿔가며 키를 무한히 만드는 것 자체가 증폭 벡터다. 한도를 IP별로 두면 IP를 바꾸면
     * 그만이므로, 창당 추적하는 키 개수에도 상한이 필요하다.
     */
    @Test
    void refusesToTrackMoreSourcesThanItsKeyCeiling() {
        AuthFailureWriteLimiter limiter = new AuthFailureWriteLimiter(10, 2, WINDOW);
        limiter.tryAcquire("ip-a", START);
        limiter.tryAcquire("ip-b", START);

        assertThat(limiter.tryAcquire("ip-c", START).allowed()).isFalse();
    }

    /** 상한에 걸린 뒤에도 이미 추적 중인 출처는 계속 기록된다. 새 키만 막는다. */
    @Test
    void keepsRecordingSourcesItAlreadyTracksAfterHittingTheCeiling() {
        AuthFailureWriteLimiter limiter = new AuthFailureWriteLimiter(10, 2, WINDOW);
        limiter.tryAcquire("ip-a", START);
        limiter.tryAcquire("ip-b", START);
        limiter.tryAcquire("ip-c", START);

        assertThat(limiter.tryAcquire("ip-a", START).allowed()).isTrue();
    }
}
