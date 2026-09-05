package io.finguard.core.permission;

/**
 * Task Passport 를 발급할 수 없는 상태.
 *
 * <p>{@code reasonCode} 는 {@code docs/06-common-conventions.md} §20 의 어휘만 담는다.
 * 계약에 없는 값을 넣으면 소비자가 그것을 실재하는 코드로 오해한다.
 */
public class PermissionNotIssuableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String reasonCode;

    public PermissionNotIssuableException(String reasonCode, String detail) {
        super(reasonCode + ": " + detail);
        this.reasonCode = reasonCode;
    }

    public String getReasonCode() {
        return reasonCode;
    }
}
