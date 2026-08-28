package io.finguard.core.domain;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * 직원이 원래 수행할 수 있는 금융업무 권한. {@code docs/01-feature-spec.md} F01.
 *
 * <p>Agent 권한의 <strong>상한선</strong>이다. Agent Effective Permission은 이 범위를 넘을 수 없다
 * ({@code AGENTS.md}). Runtime 요청 본문이 보낸 권한 목록은 근거로 쓰지 않는다.
 *
 * <p>{@code version}은 JPA 낙관적 락으로 관리한다. TaskPassport가 발급 시점의 값을
 * {@code sourceVersions}에 박아두고, 나중에 이 값이 달라지면 {@code TASK_PASSPORT_STALE}이 된다.
 * 수동으로 올리면 빠뜨리는 순간 만료된 Passport가 유효해 보이므로 자동 증가에 맡긴다.
 */
@Entity
@Table(name = "employee_authorities")
public class EmployeeAuthority {

    /** 직원당 하나. {@code employees.employee_id}를 그대로 식별자로 쓴다. */
    @Id
    @Column(name = "employee_id", nullable = false, length = 64)
    private String employeeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private EmployeeAuthorityStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "allowed_customer_scope", nullable = false, length = 32)
    private CustomerScope allowedCustomerScope;

    @ElementCollection
    @CollectionTable(
            name = "employee_authority_allowed_tools",
            joinColumns = @JoinColumn(name = "employee_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "tool", nullable = false, length = 64)
    private Set<Tool> allowedTools = EnumSet.noneOf(Tool.class);

    @ElementCollection
    @CollectionTable(
            name = "employee_authority_allowed_data",
            joinColumns = @JoinColumn(name = "employee_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "data_type", nullable = false, length = 64)
    private Set<DataType> allowedData = EnumSet.noneOf(DataType.class);

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected EmployeeAuthority() {
        // JPA
    }

    public EmployeeAuthority(
            String employeeId,
            EmployeeAuthorityStatus status,
            CustomerScope allowedCustomerScope,
            Set<Tool> allowedTools,
            Set<DataType> allowedData) {
        this.employeeId = employeeId;
        this.status = status;
        this.allowedCustomerScope = allowedCustomerScope;
        this.allowedTools = EnumSet.noneOf(Tool.class);
        this.allowedTools.addAll(allowedTools);
        this.allowedData = EnumSet.noneOf(DataType.class);
        this.allowedData.addAll(allowedData);
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public EmployeeAuthorityStatus getStatus() {
        return status;
    }

    public CustomerScope getAllowedCustomerScope() {
        return allowedCustomerScope;
    }

    public Set<Tool> getAllowedTools() {
        return Collections.unmodifiableSet(allowedTools);
    }

    public Set<DataType> getAllowedData() {
        return Collections.unmodifiableSet(allowedData);
    }

    public long getVersion() {
        return version;
    }

    public boolean isActive() {
        return status == EmployeeAuthorityStatus.ACTIVE;
    }
}
