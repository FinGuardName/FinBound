package io.finguard.core.risk;

import java.util.UUID;

/**
 * 나가는 내부 호출에 실을 추적 값. {@code docs/04-api-contract.md} §2.
 *
 * <p>Core → FastAPI 는 {@code X-FinGuard-Service-Credential} 과 함께 {@code X-Request-Id}·
 * {@code Traceparent} 를 요구한다. 이 값이 없으면 Core 요청과 ai-risk 평가 호출을 이어 볼 수 없다.
 *
 * <p>들어온 요청의 값을 그대로 전파하고, {@code X-Request-Id} 가 없으면 만든다 — §2 가 "없으면
 * Core 생성" 이라고 정한다. {@code Traceparent} 는 <strong>지어내지 않는다.</strong> W3C 형식을
 * 임의로 만들면 존재하지 않는 상위 span 을 가리키게 되고, 그건 없는 사실을 기록하는 것이다.
 */
public record RequestTrace(String requestId, String traceparent) {

    public static RequestTrace of(String incomingRequestId, String incomingTraceparent) {
        String requestId =
                incomingRequestId == null || incomingRequestId.isBlank()
                        ? UUID.randomUUID().toString()
                        : incomingRequestId;
        String traceparent =
                incomingTraceparent == null || incomingTraceparent.isBlank()
                        ? null
                        : incomingTraceparent;
        return new RequestTrace(requestId, traceparent);
    }
}
