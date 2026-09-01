package io.finguard.core.dashboard;

import java.time.Duration;
import java.time.Instant;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.finguard.core.domain.Tool;
import io.finguard.core.security.CoreApiRole;
import io.finguard.core.security.RequiresRole;

/**
 * Dashboard 조회 API. {@code docs/04-api-contract.md} §15.
 *
 * <p>Vue는 PostgreSQL을 직접 조회하지 않고 이 API만 호출한다({@code docs/06} §25).
 * 전부 읽기 전용이다 — 외부에서 AuditEvent를 고치거나 지우는 경로는 만들지 않는다.
 *
 * <p><strong>Severity와 riskFlagged로는 아직 거를 수 없다.</strong> OPA는 두 값을 내보내지만
 * ({@code policy/finguard_authz.rego}) {@code contracts/audit/audit-event.schema.json}에 이 속성이
 * 없어 저장되지 않는다. 있지도 않은 필터를 받아 조용히 무시하면 "걸었는데 안 걸린" 화면이 되므로
 * 파라미터 자체를 두지 않았다.
 */
@RestController
public class DashboardController {

    private final DashboardService dashboard;
    private final PermissionComparisonService permissionComparison;

    public DashboardController(
            DashboardService dashboard, PermissionComparisonService permissionComparison) {
        this.dashboard = dashboard;
        this.permissionComparison = permissionComparison;
    }

    @GetMapping("/api/v1/dashboard/summary")
    @RequiresRole({CoreApiRole.VIEWER, CoreApiRole.OPERATOR})
    public ResponseEntity<DashboardSummaryResponse> summary() {
        return ResponseEntity.ok(dashboard.summarize());
    }

    @GetMapping("/api/v1/audit-events")
    @RequiresRole({CoreApiRole.VIEWER, CoreApiRole.OPERATOR})
    public ResponseEntity<AuditEventPageResponse> events(
            @RequestParam(required = false) String agentId,
            @RequestParam(required = false) String caseId,
            @RequestParam(required = false) String consumerId,
            @RequestParam(required = false) Tool tool,
            @RequestParam(required = false) DashboardService.Outcome outcome,
            @RequestParam(required = false) String reasonCode,
            @RequestParam(required = false) String period,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {

        DashboardService.AuditEventQuery query =
                new DashboardService.AuditEventQuery(
                        agentId, caseId, consumerId, tool, outcome, reasonCode, startOf(period));

        return ResponseEntity.ok(dashboard.findEvents(query, page, pageSize));
    }

    @GetMapping("/api/v1/audit-events/{auditEventId}")
    @RequiresRole({CoreApiRole.VIEWER, CoreApiRole.OPERATOR})
    public ResponseEntity<AuditEventView> event(@PathVariable String auditEventId) {
        return ResponseEntity.ok(dashboard.findEvent(auditEventId));
    }

    @GetMapping("/api/v1/agent-runs/{agentRunId}/permission-comparison")
    @RequiresRole({CoreApiRole.VIEWER, CoreApiRole.OPERATOR})
    public ResponseEntity<PermissionComparisonResponse> permissionComparison(
            @PathVariable String agentRunId) {
        return ResponseEntity.ok(permissionComparison.compare(agentRunId));
    }

    /**
     * 기간 필터. 벽시계 기준으로 계산한다 — 가장 최근 기록 기준으로 잡으면 새 기록이 들어올 때마다
     * 창이 따라 움직여 같은 질의가 다른 답을 준다.
     */
    private static Instant startOf(String period) {
        if (period == null || period.isBlank() || "ALL".equals(period)) {
            return null;
        }
        return switch (period) {
            case "30M" -> Instant.now().minus(Duration.ofMinutes(30));
            case "24H" -> Instant.now().minus(Duration.ofHours(24));
            default -> throw new UnsupportedPeriodException();
        };
    }
}
