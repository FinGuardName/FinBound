package io.finguard.core.domain;

import io.finguard.core.context.ScopeState;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/**
 * 감사 기록에 남는 Scope 판정 9개. {@code contracts/audit/audit-event.schema.json}의
 * {@code $defs.scopeStatus}.
 *
 * <p>{@link io.finguard.core.context.ScopeStatus}와 값이 같지만 타입을 나눈다. 그쪽은 Resolver가
 * 계산해 응답으로 나가는 값이고 이쪽은 그 계산을 영속화한 증거다. 한 타입으로 합치면 context 패키지가
 * JPA에 묶인다.
 *
 * <p><strong>9개를 개별 컬럼으로 둔다.</strong> {@link SourceVersions}와 같은 이유다 — 스키마가
 * {@code additionalProperties: false}로 멤버 9개를 고정해 뒀으므로 JSON이나 Map으로 두면 키 오타가
 * 런타임까지 살아남고, 빠진 키와 {@code OK}인 키를 구분할 수 없다.
 *
 * <p>전부 null이면 Hibernate가 embeddable 자체를 null로 돌려준다. 아직 Resolver를 거치지 않은
 * 감사 기록이 그 상태다. 일부만 null인 상태는 DB의 all-null-or-all-non-null check가 막는다.
 */
@Embeddable
public record AuditScopeStatus(
        @Enumerated(EnumType.STRING) @Column(name = "scope_employee_authority", length = 16)
        ScopeState employeeAuthority,
        @Enumerated(EnumType.STRING) @Column(name = "scope_permission_template", length = 16)
        ScopeState permissionTemplate,
        @Enumerated(EnumType.STRING) @Column(name = "scope_case_status", length = 16)
        ScopeState caseStatus,
        @Enumerated(EnumType.STRING) @Column(name = "scope_mandate", length = 16)
        ScopeState mandate,
        @Enumerated(EnumType.STRING) @Column(name = "scope_passport_status", length = 16)
        ScopeState passportStatus,
        @Enumerated(EnumType.STRING) @Column(name = "scope_agent_binding", length = 16)
        ScopeState agentBinding,
        @Enumerated(EnumType.STRING) @Column(name = "scope_customer_scope", length = 16)
        ScopeState customerScope,
        @Enumerated(EnumType.STRING) @Column(name = "scope_tool_scope", length = 16)
        ScopeState toolScope,
        @Enumerated(EnumType.STRING) @Column(name = "scope_data_scope", length = 16)
        ScopeState dataScope) {

    public AuditScopeStatus {
        // 스키마가 9개를 모두 required로 두므로 일부만 채운 증거는 만들지 않는다.
        // 빠진 자리를 OK로 읽는 사람이 생기면 위반이 조용히 사라진다.
        requirePresent(employeeAuthority, "employeeAuthority");
        requirePresent(permissionTemplate, "permissionTemplate");
        requirePresent(caseStatus, "caseStatus");
        requirePresent(mandate, "mandate");
        requirePresent(passportStatus, "passportStatus");
        requirePresent(agentBinding, "agentBinding");
        requirePresent(customerScope, "customerScope");
        requirePresent(toolScope, "toolScope");
        requirePresent(dataScope, "dataScope");
    }

    private static void requirePresent(ScopeState state, String name) {
        if (state == null) {
            throw new IllegalArgumentException("Audit scope status requires " + name);
        }
    }
}
