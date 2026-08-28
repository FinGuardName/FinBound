package io.finguard.core.history;

/** Behavior History의 시간 창 표현이 지원 형식이 아닐 때 사용한다. */
public final class InvalidBehaviorHistoryWindowException extends RuntimeException {

    private InvalidBehaviorHistoryWindowException() {
        super("Invalid behavior history window");
    }

    static InvalidBehaviorHistoryWindowException invalid() {
        return new InvalidBehaviorHistoryWindowException();
    }
}
