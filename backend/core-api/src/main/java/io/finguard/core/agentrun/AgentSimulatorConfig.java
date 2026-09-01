package io.finguard.core.agentrun;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AgentSimulatorProperties.class)
public class AgentSimulatorConfig {
}
