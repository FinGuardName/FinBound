package io.finguard.agent.domain;

import java.util.List;

public enum AgentSimulationScenario {
    NORMAL_CREDIT_SCORE("CUST-1001", FinancialTool.CREDIT_SCORE_READ, FinancialDataType.CREDIT_SCORE),
    NORMAL_INCOME("CUST-1001", FinancialTool.INCOME_READ, FinancialDataType.INCOME),
    NORMAL_DEBT("CUST-1001", FinancialTool.DEBT_READ, FinancialDataType.DEBT),
    CASE_SCOPE_ATTACK("CUST-9999", FinancialTool.CREDIT_SCORE_READ, FinancialDataType.CREDIT_SCORE),
    TOOL_SCOPE_ATTACK("CUST-1001", FinancialTool.INCOME_READ, FinancialDataType.INCOME),
    DATA_SCOPE_ATTACK("CUST-1001", FinancialTool.CREDIT_SCORE_READ,
            FinancialDataType.CREDIT_SCORE, FinancialDataType.INCOME),
    MANDATE_SCOPE_ATTACK("CUST-1001", FinancialTool.DEBT_READ, FinancialDataType.DEBT);

    private final String targetConsumerId;
    private final FinancialTool tool;
    private final List<FinancialDataType> requestedData;

    AgentSimulationScenario(String targetConsumerId, FinancialTool tool, FinancialDataType... requestedData) {
        this.targetConsumerId = targetConsumerId;
        this.tool = tool;
        this.requestedData = List.of(requestedData);
    }

    public String targetConsumerId() {
        return targetConsumerId;
    }

    public FinancialTool tool() {
        return tool;
    }

    public List<FinancialDataType> requestedData() {
        return requestedData;
    }
}
