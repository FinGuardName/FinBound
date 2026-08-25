package io.finguard.mockfinance.service;

public class FinancialConsumerNotFoundException extends RuntimeException {
    public FinancialConsumerNotFoundException() {
        super("Mock financial data was not found");
    }
}
