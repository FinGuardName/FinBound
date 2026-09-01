package io.finguard.agent.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Validated
@ConfigurationProperties(prefix = "finguard.agent")
public record AgentProperties(
        @NotBlank String gatewayBaseUrl,
        @NotBlank String serviceCredential,
        @NotBlank String internalCredential,
        @NotNull Duration gatewayTimeout
) {
}
