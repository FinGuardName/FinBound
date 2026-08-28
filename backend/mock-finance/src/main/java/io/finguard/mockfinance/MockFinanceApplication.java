package io.finguard.mockfinance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import io.finguard.mockfinance.config.MockFinanceProperties;

@SpringBootApplication
@EnableConfigurationProperties(MockFinanceProperties.class)
public class MockFinanceApplication {
    public static void main(String[] args) {
        SpringApplication.run(MockFinanceApplication.class, args);
    }
}
