package io.finguard.core.agentrun;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Validated
@ConfigurationProperties(prefix = "finguard.agent-simulator")
public record AgentSimulatorProperties(
        @NotBlank String baseUrl,
        @NotNull Duration timeout
) {
}
