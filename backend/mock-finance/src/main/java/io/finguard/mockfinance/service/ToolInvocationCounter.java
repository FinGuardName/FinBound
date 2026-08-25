package io.finguard.mockfinance.service;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

import io.finguard.mockfinance.domain.FinancialTool;

@Component
public class ToolInvocationCounter {
    private final Map<FinancialTool, AtomicLong> counts = new EnumMap<>(FinancialTool.class);

    public ToolInvocationCounter() {
        for (FinancialTool tool : FinancialTool.values()) {
            counts.put(tool, new AtomicLong());
        }
    }

    public void increment(FinancialTool tool) {
        counts.get(tool).incrementAndGet();
    }

    public long count(FinancialTool tool) {
        return counts.get(tool).get();
    }

    public void reset() {
        counts.values().forEach(value -> value.set(0));
    }
}
