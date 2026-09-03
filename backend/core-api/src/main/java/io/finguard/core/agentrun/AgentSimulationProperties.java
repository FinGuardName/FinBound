package io.finguard.core.agentrun;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * Agent Simulator 호출 설정. {@code docs/04-api-contract.md} §3.1 — Agent는 8082에서 실행한다.
 *
 * <p>{@code @Validated}가 있어야 {@code @NotBlank}·{@code @Positive}가 기동 시점에 실제로 걸린다.
 * 없으면 어노테이션은 장식이고, 빈 자격 증명으로 떠서 모든 실행이 이유 없이 {@code FAILED}로 쌓인다 —
 * {@code InternalApiProperties}가 같은 이유로 같은 짝을 쓴다.
 *
 * <p>타임아웃 기본값은 {@code application.yml}에만 둔다. 여기서 {@code 0}을 다른 값으로 바꿔치면
 * 검증 전에 값이 바뀌어 {@code @Positive}가 아무것도 막지 못한다.
 */
@Validated
@ConfigurationProperties(prefix = "finguard.agent")
public record AgentSimulationProperties(
        @NotBlank String baseUrl,
        @NotBlank String internalCredential,
        @Positive int connectTimeoutMs,
        @Positive int readTimeoutMs) {

    public static final String INTERNAL_CREDENTIAL_HEADER = "X-FinGuard-Internal-Credential";

}
