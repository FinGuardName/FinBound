package io.finguard.core.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * TaskPassport 발급 시점의 원본 레코드 버전. {@code docs/04-api-contract.md} §4.1.
 *
 * <p>키 네 개가 계약에 고정돼 있으므로({@code docs/04-api-contract.md}:104-109)
 * {@code Map<String, Integer>}가 아니라 non-null 컬럼 네 개로 둔다. Map으로 두면 키 오타가
 * 런타임까지 살아남고, 빠진 키와 값이 0인 키를 구분할 수 없다.
 *
 * <p>현재 원본 버전과 하나라도 다르면 Passport는 {@code TASK_PASSPORT_STALE}이다.
 */
@Embeddable
public class SourceVersions {

    @Column(name = "source_version_employee_authority", nullable = false)
    private long employeeAuthority;

    @Column(name = "source_version_permission_template", nullable = false)
    private long permissionTemplate;

    @Column(name = "source_version_financial_case", nullable = false)
    private long financialCase;

    @Column(name = "source_version_consumer_mandate", nullable = false)
    private long consumerMandate;

    protected SourceVersions() {
        // JPA
    }

    public SourceVersions(
            long employeeAuthority,
            long permissionTemplate,
            long financialCase,
            long consumerMandate) {
        this.employeeAuthority = employeeAuthority;
        this.permissionTemplate = permissionTemplate;
        this.financialCase = financialCase;
        this.consumerMandate = consumerMandate;
    }

    public long getEmployeeAuthority() {
        return employeeAuthority;
    }

    public long getPermissionTemplate() {
        return permissionTemplate;
    }

    public long getFinancialCase() {
        return financialCase;
    }

    public long getConsumerMandate() {
        return consumerMandate;
    }

    /** 네 값이 모두 같아야 Passport가 최신이다. 하나라도 다르면 STALE로 다룬다. */
    public boolean matches(SourceVersions current) {
        return employeeAuthority == current.employeeAuthority
                && permissionTemplate == current.permissionTemplate
                && financialCase == current.financialCase
                && consumerMandate == current.consumerMandate;
    }
}
