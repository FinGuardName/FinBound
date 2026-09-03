package io.finguard.core.dashboard;

import java.util.List;

/** 페이지네이션된 AuditEvent 목록. 브라우저가 전체 감사 기록을 끌어가지 않게 서버가 자른다. */
public record AuditEventPageResponse(
        List<AuditEventView> items, int page, int pageSize, long totalItems, int totalPages) {
}
