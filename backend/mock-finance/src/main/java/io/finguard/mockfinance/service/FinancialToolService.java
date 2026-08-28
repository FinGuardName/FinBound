package io.finguard.mockfinance.service;

import java.util.Map;

import org.springframework.stereotype.Service;

import io.finguard.mockfinance.api.FinancialToolRequest;
import io.finguard.mockfinance.api.FinancialToolResponse;
import io.finguard.mockfinance.domain.ConsumerFinancialData;

@Service
public class FinancialToolService {
    private static final Map<String, ConsumerFinancialData> MOCK_DATA = Map.of(
            "CUST-1001", new ConsumerFinancialData(812, 85_000_000L, 25_000_000L),
            "CUST-9999", new ConsumerFinancialData(735, 62_000_000L, 41_000_000L)
    );

    private final ToolInvocationCounter invocationCounter;

    public FinancialToolService(ToolInvocationCounter invocationCounter) {
        this.invocationCounter = invocationCounter;
    }

    public FinancialToolResponse execute(FinancialToolRequest request) {
        invocationCounter.increment(request.tool());
        ConsumerFinancialData data = MOCK_DATA.get(request.targetConsumerId());
        if (data == null) {
            throw new FinancialConsumerNotFoundException();
        }

        Map<String, Object> result = switch (request.tool()) {
            case CREDIT_SCORE_READ -> Map.of("creditScore", data.creditScore());
            case INCOME_READ -> Map.of("annualIncome", data.annualIncome());
            case DEBT_READ -> Map.of("totalDebt", data.totalDebt());
        };

        return new FinancialToolResponse(
                request.requestId(),
                request.tool(),
                request.targetConsumerId(),
                result
        );
    }
}
