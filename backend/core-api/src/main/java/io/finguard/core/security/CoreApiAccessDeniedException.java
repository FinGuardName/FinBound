package io.finguard.core.security;

import io.finguard.core.domain.ReasonCode;

/**
 * 인증은 됐지만 이 요청을 할 자격이 없다. {@code docs/04-api-contract.md} §2 · §3.
 *
 * <p>401과 갈라 두는 이유는 사유가 다르기 때문이다. 401은 "당신이 누구인지 모른다"이고 이것은
 * "누구인지는 알지만 그 일은 당신 것이 아니다"이다.
 *
 * <p>거부된 값(제시한 {@code employeeId} 등)은 담지 않는다 — {@code docs/06-common-conventions.md} §26.
 */
public class CoreApiAccessDeniedException extends RuntimeException {

    private final ReasonCode reasonCode;

    public CoreApiAccessDeniedException(ReasonCode reasonCode, String message) {
        super(message);
        this.reasonCode = reasonCode;
    }

    public ReasonCode getReasonCode() {
        return reasonCode;
    }
}
