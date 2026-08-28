package io.finguard.core.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;

/**
 * 브라우저(Vue) → Core {@code /api/v1/**} 인증 설정. {@code docs/04-api-contract.md} §2.
 *
 * <p>셋 중 하나라도 비어 있으면 기동에 실패한다. 인증 없는 {@code /api/v1/**}가 떠 있는 상태를
 * 만들지 않는다.
 *
 * <p>두 Credential이 같으면 역할 구분이 사라져 Viewer가 AgentRun을 생성할 수 있게 된다.
 * 설정 실수로 권한 경계가 조용히 무너지는 경로라 기동을 막는다.
 */
@Validated
@ConfigurationProperties(prefix = "finguard.api")
public record CoreApiProperties(
        @NotBlank String viewerCredential,
        @NotBlank String operatorCredential,
        @NotBlank String operatorEmployeeId) {

    @AssertTrue(message = "viewer and operator credentials must differ")
    public boolean hasDistinctCredentials() {
        return viewerCredential == null
                || operatorCredential == null
                || !viewerCredential.equals(operatorCredential);
    }
}
