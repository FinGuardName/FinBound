package io.finguard.mockfinance.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.finguard.mockfinance.service.FinancialToolService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/internal/v1/finance/tool-calls")
public class FinancialToolController {
    private final FinancialToolService financialToolService;

    public FinancialToolController(FinancialToolService financialToolService) {
        this.financialToolService = financialToolService;
    }

    @PostMapping
    public ResponseEntity<FinancialToolResponse> execute(
            @Valid @RequestBody FinancialToolRequest request
    ) {
        return ResponseEntity.ok(financialToolService.execute(request));
    }
}
