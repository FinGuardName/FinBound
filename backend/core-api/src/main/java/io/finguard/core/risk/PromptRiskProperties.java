package io.finguard.core.risk;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * ai-risk Prompt Detector 호출 설정. {@code docs/04-api-contract.md} §8.
 *
 * <p>{@code @Validated} 가 있어야 {@code @NotBlank}·{@code @Positive} 가 기동 시점에 실제로 걸린다.
 * 없으면 어노테이션은 장식이고, 빈 자격 증명으로 떠서 모든 평가가 이유 없이 실패한다 —
 * {@code AgentSimulationProperties} 가 같은 이유로 같은 짝을 쓴다.
 */
@Validated
@ConfigurationProperties(prefix = "finguard.risk")
public record PromptRiskProperties(
        @NotBlank String baseUrl,
        @NotBlank String serviceCredential,
        @Positive int connectTimeoutMs,
        @Positive int readTimeoutMs) {

    /** ai-risk 가 받는 헤더. Agent 의 {@code X-FinGuard-Internal-Credential} 과 다르다 — docs/04 §2. */
    public static final String SERVICE_CREDENTIAL_HEADER = "X-FinGuard-Service-Credential";
}
