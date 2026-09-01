package io.finguard.core.security;

/** {@code /api/v1/**} 호출자의 역할. {@code docs/04-api-contract.md} §2. */
public enum CoreApiRole {

    /** §15 Dashboard 조회만 할 수 있다. Employee에 결합되지 않는다. */
    VIEWER,

    /** Dashboard 조회에 더해 AgentRun을 생성할 수 있다. 단일 Employee에 결합된다. */
    OPERATOR
}
