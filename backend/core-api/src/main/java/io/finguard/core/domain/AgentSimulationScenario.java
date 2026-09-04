package io.finguard.core.domain;

/**
 * P0 Simulator 시나리오. {@code docs/04-api-contract.md} §3.1.
 *
 * <p>Agent 모듈에 같은 이름의 enum이 있지만 <strong>여기에 따로 둔다.</strong> 모듈이 달라 타입을
 * 공유할 수 없고, 공유해서도 안 된다 — {@code PromptAttackType}이 Core와 ai-risk 양쪽에 나란히
 * 있는 것과 같다. 값의 주인은 어느 한쪽 코드가 아니라 계약 문서다.
 *
 * <p>어느 고객을 노리는지는 Agent가 정한다. Core는 "정상 조회인가 범위 밖 시도인가"만 전달한다 —
 * 시연용 고객 ID를 Core가 알 이유가 없다. 같은 이유로 Tool과 요청 자료도 여기 없다.
 * {@code docs/04} §3.1이 "Scenario는 요청 생성만 결정한다"고 못 박았다.
 *
 * <p><strong>순서의 근거는 Agent 모듈의 같은 이름 enum이다.</strong> §3.1은 값 일곱 개를 정의하지만
 * 한 자리에 모아 두지 않았다 — 표에는 새로 추가된 다섯 개만 있고 나머지 둘은 앞 문단에 있다.
 * 그래서 정식 순서를 문서에서 읽어낼 수 없다. 타입은 공유하지 않지만 어휘는 하나여야 하므로
 * <strong>값과 순서를 Agent 쪽에 맞춘다.</strong> 결과적으로 정상 3개가 먼저, 공격 4개가 뒤다.
 *
 * <p>공격 시나리오라고 해서 반드시 BLOCK이 나오지는 않는다. §3.1이 밝힌 대로 모든 권한을 허용하는
 * 기본 Seed에서는 이름이 공격이어도 ALLOW다. <strong>차단은 서버 Context가 만들지 이 이름이 만들지
 * 않는다.</strong> Agent도 Core도 Scope를 계산하거나 Reason Code를 지어내지 않는다.
 */
public enum AgentSimulationScenario {

    /** 자기 Case의 고객을 조회한다. 기본값이다 — 지정하지 않으면 공격을 시연하지 않는다. */
    NORMAL_CREDIT_SCORE,

    /** 자기 Case의 고객에게 허용된 소득 조회다. */
    NORMAL_INCOME,

    /** 자기 Case의 고객에게 허용된 부채 조회다. */
    NORMAL_DEBT,

    /** Case가 허용하지 않은 고객을 조회하려 한다. BLOCK 시연 경로다. */
    CASE_SCOPE_ATTACK,

    /** Passport가 허용하지 않은 Tool을 쓰려 한다. 요청 자체는 {@code NORMAL_INCOME}과 같다. */
    TOOL_SCOPE_ATTACK,

    /** 허용된 Tool로 허용되지 않은 자료까지 함께 요청한다. */
    DATA_SCOPE_ATTACK,

    /** 고객 위임 범위 밖의 자료를 조회하려 한다. 요청 자체는 {@code NORMAL_DEBT}와 같다. */
    MANDATE_SCOPE_ATTACK,
}
