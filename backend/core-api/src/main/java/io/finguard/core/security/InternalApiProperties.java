package io.finguard.core.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

/**
 * Core Internal API 설정.
 *
 * <p>{@code credential}이 비어 있으면 기동에 실패한다. 인증이 없는 Internal API가 떠 있는 상태를 만들지 않는다.
 */
@Validated
@ConfigurationProperties(prefix = "finguard.internal")
public record InternalApiProperties(@NotBlank String credential) {
}
