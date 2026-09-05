package io.finguard.core.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 직원 식별자의 기준점.
 *
 * <p>권한 자체는 {@link EmployeeAuthority}가 갖는다. 이 테이블은 식별자가 실재하는지를 보증하는
 * 역할만 한다 — {@code docs/06-common-conventions.md} §2에 따라 ID는 식별을 위한 값이며
 * 인증수단이 아니다.
 */
@Entity
@Table(name = "employees")
public class Employee {

    @Id
    @Column(name = "employee_id", nullable = false, length = 64)
    private String employeeId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Employee() {
        // JPA
    }

    public Employee(String employeeId, Instant createdAt) {
        this.employeeId = employeeId;
        this.createdAt = createdAt;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
