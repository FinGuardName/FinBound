package io.finguard.gateway.exception;

/**
 * Downstream 호출 실패. {@code downstreamReached}는 요청이 상대 서버에 도달했는지 여부를 나타낸다.
 * connection refused/DNS 실패 등 연결 자체가 성사되지 않은 경우 false, HTTP 오류 응답을 받은 경우 true.
 */
public class DownstreamUnavailableException extends RuntimeException {

    private final boolean downstreamReached;

    public DownstreamUnavailableException(String message, boolean downstreamReached) {
        super(message);
        this.downstreamReached = downstreamReached;
    }

    public DownstreamUnavailableException(String message, Throwable cause, boolean downstreamReached) {
        super(message, cause);
        this.downstreamReached = downstreamReached;
    }

    public boolean downstreamReached() {
        return downstreamReached;
    }
}
