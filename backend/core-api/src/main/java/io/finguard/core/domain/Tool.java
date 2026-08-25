package io.finguard.core.domain;

/**
 * Runtime Tool. {@code docs/06-common-conventions.md} §16 — P0에서 문자열 자유입력을 허용하지 않는다.
 *
 * <p>각 Tool은 자기가 읽는 Data 종류를 안다. 이 연결이 없으면 소비자가 {@code INCOME}을 거부했는데도
 * Passport에 {@code INCOME_READ}가 남고, 요청자가 {@code requestedData}를 비워 보내면
 * {@code dataScope} 검사를 통과할 수 있다.
 */
public enum Tool {
    CREDIT_SCORE_READ(DataType.CREDIT_SCORE),
    INCOME_READ(DataType.INCOME),
    DEBT_READ(DataType.DEBT);

    private final DataType requiredData;

    Tool(DataType requiredData) {
        this.requiredData = requiredData;
    }

    /** 이 Tool을 쓰면 반드시 읽게 되는 Data 종류. */
    public DataType requiredData() {
        return requiredData;
    }
}
