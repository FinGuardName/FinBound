package io.finguard.mockfinance.service;

import java.util.Map;

import org.springframework.stereotype.Service;

import io.finguard.mockfinance.api.FinancialToolRequest;
import io.finguard.mockfinance.api.FinancialToolResponse;
import io.finguard.mockfinance.domain.ConsumerFinancialData;

@Service
public class FinancialToolService {
    // 시드(db/local/R__demo_seed.sql)와 화면(frontend/src/mock/fixtures.js)이 쓰는 고객은 여기에도
    // 있어야 한다. 없으면 인가는 ALLOW 인데 조회가 DOWNSTREAM_ERROR 로 실패한다.
    //
    // CUST-1002·CUST-1003 은 Mandate 가 좁아 항상 차단되므로 여기까지 오지 않는다. 그럼에도 넣는
    // 이유는, 차단이 풀렸을 때 조용히 실패하는 대신 값이 나오게 하기 위해서다 — 없는 고객이라
    // 실패하는 것과 권한이 없어 차단되는 것은 전혀 다른 이야기다.
    private static final Map<String, ConsumerFinancialData> MOCK_DATA = Map.of(
            "CUST-1001", new ConsumerFinancialData(812, 85_000_000L, 25_000_000L),
            "CUST-1002", new ConsumerFinancialData(704, 51_000_000L, 33_000_000L),
            "CUST-1003", new ConsumerFinancialData(688, 47_000_000L, 39_000_000L),
            "CUST-2001", new ConsumerFinancialData(776, 93_000_000L, 21_000_000L),
            "CUST-3001", new ConsumerFinancialData(742, 58_000_000L, 30_000_000L),
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
