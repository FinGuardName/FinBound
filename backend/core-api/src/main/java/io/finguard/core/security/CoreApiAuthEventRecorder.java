package io.finguard.core.security;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import io.finguard.core.domain.ReasonCode;
import io.finguard.core.securityevent.SecurityEventService;
import jakarta.servlet.http.HttpServletRequest;

/**
 * {@code /api/v1/**}의 인증·인가 거부를 {@code SecurityAuthEvent}로 남긴다.
 * {@code docs/04-api-contract.md} §2.
 *
 * <p><strong>기록 실패가 거부 결정을 뒤집지 못한다.</strong> 여기서 모든 예외를 삼키는 이유다. 401·403은
 * 이미 내려진 판단이고, 그 판단을 적어두지 못한 것은 별개의 문제다. 적지 못했다고 통과시키면 기록
 * 장애가 곧 인증 우회가 된다.
 *
 * <p>예외를 {@link SecurityEventService} 안이 아니라 여기서 잡는 것도 이유가 있다. 그쪽은
 * {@code @Transactional}이라 <strong>커밋이 메서드 반환 뒤 프록시에서</strong> 일어나므로, 메서드 안의
 * try/catch는 커밋 단계의 실패를 잡지 못한다. 트랜잭션 경계 바깥인 여기가 잡을 수 있는 자리다.
 */
@Component
public class CoreApiAuthEventRecorder {

    private static final Logger log = LoggerFactory.getLogger(CoreApiAuthEventRecorder.class);

    /** {@code docs/04} §2 — 브라우저에서 오는 경로에는 Gateway가 없어 Core가 만든다. */
    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    private static final String TRACEPARENT_HEADER = "Traceparent";

    private final SecurityEventService securityEvents;
    private final Clock clock;
    private final AuthFailureWriteLimiter limiter;

    public CoreApiAuthEventRecorder(
            SecurityEventService securityEvents, Clock clock, AuthFailureWriteLimiter limiter) {
        this.securityEvents = securityEvents;
        this.clock = clock;
        this.limiter = limiter;
    }

    public void record(HttpServletRequest request, ReasonCode reasonCode) {
        String requestId = requestId(request);
        try {
            Instant now = clock.instant();
            // 출처는 한도의 키로만 쓰고 저장하지 않는다. 소금 없는 IP 해시는 되돌릴 수 있어서
            // "비식별"이 성립하지 않는다 — SecurityEventService.recordCoreApiAuthFailure Javadoc.
            AuthFailureWriteLimiter.Decision decision =
                    limiter.tryAcquire(request.getRemoteAddr(), now);

            if (decision.droppedInClosedWindow() > 0) {
                // 버린 건수를 한 건으로 남긴다. 이게 없으면 "조용했다"와 "폭주를 통째로 버렸다"가
                // 기록상 같아 보인다.
                log.warn(
                        "Security auth event writes throttled. dropped={} requestId={}",
                        decision.droppedInClosedWindow(),
                        requestId);
                securityEvents.recordCoreApiWriteThrottled(
                        requestId, decision.droppedInClosedWindow(), now);
            }

            if (!decision.allowed()) {
                return;
            }

            securityEvents.recordCoreApiAuthFailure(
                    requestId, request.getHeader(TRACEPARENT_HEADER), reasonCode, now);
        } catch (RuntimeException exception) {
            // 거부는 이미 응답에 실렸다. 여기서 할 수 있는 건 기록을 못 남겼다고 알리는 것뿐이다.
            log.error(
                    "Security auth event write failed. reasonCode={} requestId={}",
                    reasonCode,
                    requestId,
                    exception);
        }
    }

    /**
     * 제시된 {@code X-Request-Id}를 그대로 신뢰하되 길이만 자른다.
     *
     * <p>이 값은 인증되지 않은 클라이언트가 보낸 것이라 내용은 믿을 게 못 된다. 다만 컬럼이
     * {@code varchar(64)}이므로 넘치면 저장이 통째로 실패한다 — 긴 헤더 하나로 기록을 막을 수 있게
     * 두지 않는다.
     */
    private String requestId(HttpServletRequest request) {
        String presented = request.getHeader(REQUEST_ID_HEADER);
        if (presented == null || presented.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return presented.length() > 64 ? presented.substring(0, 64) : presented;
    }
}
