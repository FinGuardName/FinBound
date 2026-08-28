package io.finguard.mockfinance.domain;

public record ConsumerFinancialData(
        int creditScore,
        long annualIncome,
        long totalDebt
) {
}
