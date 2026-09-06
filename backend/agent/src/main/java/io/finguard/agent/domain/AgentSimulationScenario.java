package io.finguard.agent.domain;

import java.util.List;

/**
 * 데모 시나리오. 어떤 Tool 로 누구의 무슨 Data 를 조회할지를 정한다.
 *
 * <p><strong>대상 고객은 두 가지 방식으로 정해진다.</strong> 정상 셋은 실행이 맡은 사건의 고객을
 * 그대로 조회한다 — 사건마다 대상이 다른 것이 업무의 본질이다. 공격 넷만 자기 고객을 고정한다.
 * 사건 밖 고객을 노리거나 Mandate 가 좁은 고객을 노리는 것이 <strong>공격의 내용 자체</strong>여서,
 * 사건의 고객을 쓰면 공격이 공격이 아니게 되기 때문이다.
 *
 * <p>공격 셋(TOOL/DATA/MANDATE)이 의도한 사유로 막히려면 <strong>실행의 고객이 그 공격의 고객과
 * 같아야 한다.</strong> Core 의 Context Resolve 가 Consumer Mandate 를 요청 대상이 아니라 Passport 의
 * 고객으로 조회하기 때문이다. 실행이 CUST-1001 짜리면 CUST-1002 의 좁은 Mandate 는 읽히지도 않고
 * 그냥 CASE_SCOPE_VIOLATION 이 난다. 검증 절차는 {@code infrastructure/tests/deployed-scenarios.py}.
 */
public enum AgentSimulationScenario {
    NORMAL_CREDIT_SCORE(TargetMode.CASE_CONSUMER, null,
            FinancialTool.CREDIT_SCORE_READ, FinancialDataType.CREDIT_SCORE),
    NORMAL_INCOME(TargetMode.CASE_CONSUMER, null,
            FinancialTool.INCOME_READ, FinancialDataType.INCOME),
    NORMAL_DEBT(TargetMode.CASE_CONSUMER, null,
            FinancialTool.DEBT_READ, FinancialDataType.DEBT),
    CASE_SCOPE_ATTACK(TargetMode.FIXED_FIXTURE, "CUST-9999",
            FinancialTool.CREDIT_SCORE_READ, FinancialDataType.CREDIT_SCORE),
    // 공격 셋은 Mandate가 좁은 Fixture 고객을 노린다. CUST-1001은 Tool·Data 셋을 모두 허용해
    // 이름만 공격이고 결과는 ALLOW였다 — 이슈 #94, docs/04-api-contract.md §3.1.
    TOOL_SCOPE_ATTACK(TargetMode.FIXED_FIXTURE, "CUST-1002",
            FinancialTool.INCOME_READ, FinancialDataType.INCOME),
    DATA_SCOPE_ATTACK(TargetMode.FIXED_FIXTURE, "CUST-1002",
            FinancialTool.CREDIT_SCORE_READ,
            FinancialDataType.CREDIT_SCORE, FinancialDataType.INCOME),
    MANDATE_SCOPE_ATTACK(TargetMode.FIXED_FIXTURE, "CUST-1003",
            FinancialTool.DEBT_READ, FinancialDataType.DEBT);

    /** 대상 고객을 어디서 얻는지. {@code null} 로 구분하면 읽는 사람이 의미를 추측해야 한다. */
    public enum TargetMode {
        /** 실행이 맡은 사건의 고객. 정상 업무는 이쪽이다. */
        CASE_CONSUMER,
        /** 시나리오가 고정한 Fixture 고객. 공격만 이쪽이다. */
        FIXED_FIXTURE
    }

    private final TargetMode targetMode;
    private final String fixedConsumerId;
    private final FinancialTool tool;
    private final List<FinancialDataType> requestedData;

    AgentSimulationScenario(TargetMode targetMode, String fixedConsumerId,
                            FinancialTool tool, FinancialDataType... requestedData) {
        if (targetMode == TargetMode.FIXED_FIXTURE
                && (fixedConsumerId == null || fixedConsumerId.isBlank())) {
            throw new IllegalArgumentException("FIXED_FIXTURE scenario needs a consumer");
        }
        if (targetMode == TargetMode.CASE_CONSUMER && fixedConsumerId != null) {
            throw new IllegalArgumentException("CASE_CONSUMER scenario must not pin a consumer");
        }
        this.targetMode = targetMode;
        this.fixedConsumerId = fixedConsumerId;
        this.tool = tool;
        this.requestedData = List.of(requestedData);
    }

    public TargetMode targetMode() {
        return targetMode;
    }

    /**
     * 실제로 조회할 고객을 정한다.
     *
     * @param caseConsumerId Core 가 알려준 사건의 고객. 공격 시나리오는 이 값을 무시한다.
     */
    public String resolveTargetConsumerId(String caseConsumerId) {
        if (targetMode == TargetMode.FIXED_FIXTURE) {
            return fixedConsumerId;
        }
        if (caseConsumerId == null || caseConsumerId.isBlank()) {
            throw new IllegalArgumentException(
                    "Case consumer is required for " + name() + " — Core must send it");
        }
        return caseConsumerId;
    }

    public FinancialTool tool() {
        return tool;
    }

    public List<FinancialDataType> requestedData() {
        return requestedData;
    }
}
