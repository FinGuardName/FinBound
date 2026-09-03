package io.finguard.core.domain;

/**
 * P0 Simulator 시나리오. {@code docs/04-api-contract.md} §3.1.
 *
 * <p>Agent 모듈에 같은 이름의 enum이 있지만 <strong>여기에 따로 둔다.</strong> 모듈이 달라 타입을
 * 공유할 수 없고, 공유해서도 안 된다 — {@code PromptAttackType}이 Core와 ai-risk 양쪽에 나란히
 * 있는 것과 같다. 값의 주인은 어느 한쪽 코드가 아니라 계약 문서다.
 *
 * <p>어느 고객을 노리는지는 Agent가 정한다. Core는 "정상 조회인가 범위 밖 시도인가"만 전달한다 —
 * 시연용 고객 ID를 Core가 알 이유가 없다.
 */
public enum AgentSimulationScenario {

    /** 자기 Case의 고객을 조회한다. 기본값이다 — 지정하지 않으면 공격을 시연하지 않는다. */
    NORMAL_CREDIT_SCORE,

    /** Case가 허용하지 않은 고객을 조회하려 한다. BLOCK 시연 경로다. */
    CASE_SCOPE_ATTACK,
}
