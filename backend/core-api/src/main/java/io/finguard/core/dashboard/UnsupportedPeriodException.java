package io.finguard.core.dashboard;

/** 대시보드 기간 필터에 정의되지 않은 값이 왔다. 서버 고장이 아니라 호출자의 입력 오류다. */
public class UnsupportedPeriodException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public UnsupportedPeriodException() {
        // 받은 값을 메시지에 담지 않는다 — docs/06 §26.
        super("Unsupported dashboard period");
    }
}
