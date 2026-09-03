package io.finguard.core.dashboard;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.finguard.core.domain.AuditEvent;
import io.finguard.core.domain.AuditStatus;
import io.finguard.core.domain.PolicyDecision;
import io.finguard.core.domain.Severity;
import io.finguard.core.domain.Tool;
import io.finguard.core.repository.AuditEventRepository;

/**
 * Dashboard 조회. {@code docs/04-api-contract.md} §15.
 *
 * <p>읽기 전용이다 — 외부에서 AuditEvent를 수정·삭제하는 경로를 만들지 않는다
 * ({@code docs/01-feature-spec.md} F19).
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    /** 한 번에 끌어오는 최대 행 수. 브라우저가 무한 목록을 받지 않게 서버에서 자른다(docs/06 §25). */
    private static final int MAX_PAGE_SIZE = 100;

    private final AuditEventRepository auditEvents;

    public DashboardService(AuditEventRepository auditEvents) {
        this.auditEvents = auditEvents;
    }

    public DashboardSummaryResponse summarize() {
        long total = auditEvents.count();
        long error = auditEvents.countByStatus(AuditStatus.ERROR);
        long allow = auditEvents.countByStatusNotAndDecision(AuditStatus.ERROR, PolicyDecision.ALLOW);
        long block = auditEvents.countByStatusNotAndDecision(AuditStatus.ERROR, PolicyDecision.BLOCK);
        return new DashboardSummaryResponse(total, allow, block, error);
    }

    public AuditEventPageResponse findEvents(AuditEventQuery query, int page, int pageSize) {
        int normalizedSize = Math.clamp(pageSize, 1, MAX_PAGE_SIZE);
        int normalizedPage = Math.max(page, 1);

        // docs/06 §25 — 기본 정렬은 requestedAt DESC. 같은 시각이면 id로 갈라 페이지 경계에서
        // 같은 행이 두 번 나오거나 건너뛰는 일을 막는다.
        Sort sort = Sort.by(Sort.Direction.DESC, "requestedAt").and(Sort.by(Sort.Direction.DESC, "auditEventId"));
        Page<AuditEvent> found =
                auditEvents.findAll(
                        query.toSpecification(), PageRequest.of(normalizedPage - 1, normalizedSize, sort));

        List<AuditEventView> items = found.getContent().stream().map(AuditEventView::of).toList();
        return new AuditEventPageResponse(
                items,
                normalizedPage,
                normalizedSize,
                found.getTotalElements(),
                Math.max(found.getTotalPages(), 1));
    }

    public AuditEventView findEvent(String auditEventId) {
        return auditEvents
                .findById(auditEventId)
                .map(AuditEventView::of)
                .orElseThrow(() -> new AuditEventNotFoundException(auditEventId));
    }

    /** 저장된 감사 컬럼으로만 거른다. 점수에서 정책 결과를 다시 계산하지 않는다. */
    public record AuditEventQuery(
            String agentId,
            String caseId,
            String targetConsumerId,
            Tool requestedTool,
            Outcome outcome,
            Severity severity,
            Boolean riskOnly,
            String reasonCode,
            Instant requestedAfter) {

        Specification<AuditEvent> toSpecification() {
            Specification<AuditEvent> spec = (root, cq, cb) -> cb.conjunction();
            if (agentId != null) {
                spec = spec.and((root, cq, cb) -> cb.equal(root.get("agentId"), agentId));
            }
            if (caseId != null) {
                spec = spec.and((root, cq, cb) -> cb.equal(root.get("caseId"), caseId));
            }
            if (targetConsumerId != null) {
                spec = spec.and((root, cq, cb) -> cb.equal(root.get("targetConsumerId"), targetConsumerId));
            }
            if (requestedTool != null) {
                spec = spec.and((root, cq, cb) -> cb.equal(root.get("requestedTool"), requestedTool));
            }
            if (requestedAfter != null) {
                spec = spec.and((root, cq, cb) -> cb.greaterThanOrEqualTo(root.get("requestedAt"), requestedAfter));
            }
            if (reasonCode != null) {
                spec = spec.and((root, cq, cb) -> cb.isMember(reasonCode, root.get("reasonCodes")));
            }
            if (outcome != null) {
                spec = spec.and(outcome.toSpecification());
            }
            if (severity != null) {
                spec = spec.and((root, cq, cb) -> cb.equal(root.get("severity"), severity));
            }
            if (Boolean.TRUE.equals(riskOnly)) {
                spec = spec.and((root, cq, cb) -> cb.isTrue(root.get("riskFlagged")));
            }
            return spec;
        }
    }

    /**
     * 화면에 보이는 처리 결과. 판정(ALLOW/BLOCK)과 시스템 결과(COMPLETED/ERROR)는 다른 축인데
     * 사용자에게는 한 칸으로 보이므로, ERROR가 판정을 덮는다({@code docs/06} §12).
     */
    public enum Outcome {
        ALLOW,
        BLOCK,
        ERROR;

        Specification<AuditEvent> toSpecification() {
            if (this == ERROR) {
                return (root, cq, cb) -> cb.equal(root.get("status"), AuditStatus.ERROR);
            }
            PolicyDecision decision = this == ALLOW ? PolicyDecision.ALLOW : PolicyDecision.BLOCK;
            return (root, cq, cb) ->
                    cb.and(
                            cb.notEqual(root.get("status"), AuditStatus.ERROR),
                            cb.equal(root.get("decision"), decision));
        }
    }
}
