package io.finguard.core.security;

/**
 * 검증을 마친 {@code /api/v1/**} 호출자.
 *
 * <p>{@code employeeId}는 OPERATOR일 때만 있다. 이 값은 Core 설정에서 온 것이지 Request Body에서
 * 온 것이 아니다 — {@code docs/04-api-contract.md} §1.4가 Body의 식별자를 신뢰하지 말라고 한다.
 */
public record CoreApiPrincipal(CoreApiRole role, String employeeId) {
}
