package io.finguard.mockfinance.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import io.finguard.mockfinance.service.FinancialConsumerNotFoundException;

@RestControllerAdvice
public class FinancialApiExceptionHandler {
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<FinancialApiError> handleInvalidRequest() {
        return ResponseEntity.badRequest().body(new FinancialApiError(
                "INVALID_TOOL_REQUEST",
                "The financial tool request is invalid"
        ));
    }

    @ExceptionHandler(FinancialConsumerNotFoundException.class)
    public ResponseEntity<FinancialApiError> handleConsumerNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new FinancialApiError(
                "FINANCIAL_DATA_NOT_FOUND",
                "Mock financial data is not available for the requested consumer"
        ));
    }
}
