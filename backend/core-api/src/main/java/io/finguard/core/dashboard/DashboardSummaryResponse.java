package io.finguard.core.dashboard;

/**
 * Dashboard 상단 집계. {@code docs/04-api-contract.md} §15.
 *
 * <p>{@code total}은 {@code allow + block + error}와 같지 않을 수 있다 — 아직 판정이 없는
 * PROCESSING 기록이 total에만 들어간다. 진행 중인 요청을 숨기지 않기 위해서다.
 */
public record DashboardSummaryResponse(long total, long allow, long block, long error) {
}
