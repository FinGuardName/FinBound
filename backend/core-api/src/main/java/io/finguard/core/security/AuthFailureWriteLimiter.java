package io.finguard.core.security;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * 인증되지 않은 요청이 {@code security_auth_events}를 무한히 불리지 못하게 한다.
 *
 * <p><strong>제한하는 것은 기록이지 요청 거부가 아니다.</strong> 401·403은 한도와 무관하게 언제나
 * 나간다. 한도가 차면 통과시키는 구조로 만들면 그 한도를 채우는 것 자체가 우회 수단이 된다.
 *
 * <p>고정 시간창을 쓴다. 창 경계에서 순간적으로 두 배까지 통과할 수 있는 방식이지만, 여기서 지키려는
 * 것은 정확한 속도가 아니라 <em>저장소가 유한하다</em>는 성질이라 그 오차는 문제가 되지 않는다.
 *
 * <p>출처별 한도만 두면 출처를 바꿔가며 키를 늘리는 것이 그대로 증폭 벡터가 되므로, 창당 추적하는
 * 키 개수에도 상한을 둔다. 이 맵 자체가 미인증 트래픽이 키우는 자료구조이기 때문이다.
 */
public class AuthFailureWriteLimiter {

    /** 한 번의 판정 결과와, 이 호출이 닫아버린 직전 창이 버린 건수. */
    public record Decision(boolean allowed, long droppedInClosedWindow) {
    }

    private final int maxPerSource;
    private final int maxSources;
    private final Duration window;

    private final Map<String, Integer> counts = new HashMap<>();
    private Instant windowStart;
    private long dropped;

    public AuthFailureWriteLimiter(int maxPerSource, int maxSources, Duration window) {
        this.maxPerSource = maxPerSource;
        this.maxSources = maxSources;
        this.window = window;
    }

    /**
     * 이 출처의 기록을 한 건 허용할지 정한다.
     *
     * <p>{@code synchronized}인 이유는 이 경로가 인증 실패 경로이기 때문이다. 처리량을 끌어올릴 만한
     * 자리가 아니고, 카운터가 어긋나면 상한이 상한이 아니게 된다.
     */
    public synchronized Decision tryAcquire(String source, Instant now) {
        long droppedInClosedWindow = rotateIfWindowElapsed(now);

        boolean tracked = counts.containsKey(source);
        if (!tracked && counts.size() >= maxSources) {
            // 새 출처를 더 받지 않는다. 이미 추적 중인 출처는 계속 기록된다.
            dropped++;
            return new Decision(false, droppedInClosedWindow);
        }

        int used = counts.merge(source, 1, Integer::sum);
        if (used > maxPerSource) {
            dropped++;
            return new Decision(false, droppedInClosedWindow);
        }
        return new Decision(true, droppedInClosedWindow);
    }

    /** @return 방금 닫힌 창이 버린 건수. 창이 아직 살아 있으면 0. */
    private long rotateIfWindowElapsed(Instant now) {
        if (windowStart == null) {
            windowStart = now;
            return 0;
        }
        if (Duration.between(windowStart, now).compareTo(window) < 0) {
            return 0;
        }
        long closed = dropped;
        counts.clear();
        dropped = 0;
        windowStart = now;
        return closed;
    }
}
