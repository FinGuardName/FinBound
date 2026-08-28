package io.finguard.mockfinance.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

@Validated
@ConfigurationProperties(prefix = "finguard.mock-finance")
public record MockFinanceProperties(@NotBlank String internalCredential) {
}
