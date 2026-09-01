package io.finguard.core.context;

/** 필수 Context를 찾거나 하나의 신뢰 가능한 그래프로 결합할 수 없을 때의 404 오류. */
public class ContextLookupException extends RuntimeException {

    private final String reasonCode;

    private ContextLookupException(String reasonCode) {
        super(reasonCode);
        this.reasonCode = reasonCode;
    }

    public static ContextLookupException passportNotFound() {
        return new ContextLookupException("TASK_PASSPORT_NOT_FOUND");
    }

    public static ContextLookupException contextNotFound() {
        return new ContextLookupException("CONTEXT_NOT_FOUND");
    }

    public String getReasonCode() {
        return reasonCode;
    }
}
