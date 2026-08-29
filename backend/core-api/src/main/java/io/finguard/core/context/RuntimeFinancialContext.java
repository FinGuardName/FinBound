package io.finguard.core.context;

import io.finguard.core.domain.ConsumerMandate;
import io.finguard.core.domain.EmployeeAuthority;
import io.finguard.core.domain.FinancialCase;
import io.finguard.core.domain.PermissionTemplate;
import io.finguard.core.domain.SourceVersions;
import io.finguard.core.domain.TaskPassport;

/** DB에서 조회하고 관계를 검증한 뒤 Resolver에 넘기는 신뢰 가능한 금융 Context. */
public record RuntimeFinancialContext(
        EmployeeAuthority authority,
        PermissionTemplate template,
        FinancialCase financialCase,
        ConsumerMandate mandate,
        TaskPassport passport,
        SourceVersions currentSourceVersions) {
}
