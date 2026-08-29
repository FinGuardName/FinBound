package io.finguard.core.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.finguard.core.domain.ConsumerMandate;
import io.finguard.core.domain.ConsumerMandateStatus;
import io.finguard.core.domain.CustomerScope;
import io.finguard.core.domain.DataType;
import io.finguard.core.domain.EmployeeAuthority;
import io.finguard.core.domain.EmployeeAuthorityStatus;
import io.finguard.core.domain.FinancialCase;
import io.finguard.core.domain.FinancialCaseStatus;
import io.finguard.core.domain.PermissionTemplate;
import io.finguard.core.domain.PermissionTemplateStatus;
import io.finguard.core.domain.SourceVersions;
import io.finguard.core.domain.TaskPassport;
import io.finguard.core.domain.TaskPassportStatus;
import io.finguard.core.domain.TaskType;
import io.finguard.core.domain.Tool;

class FinancialContextResolverTest {

    private static final Instant NOW = Instant.parse("2026-08-25T12:00:00Z");
    private static final Set<Tool> ALL_TOOLS = Set.of(Tool.values());
    private static final Set<DataType> ALL_DATA = Set.of(DataType.values());
    private static final SourceVersions CURRENT_VERSIONS = new SourceVersions(0, 0, 0, 0);

    private final FinancialContextResolver resolver = new FinancialContextResolver();

    @Test
    void returnsNineOkStatusesWhenTheRequestIsInsideEveryScope() {
        ScopeStatus status = resolve(activeContext());

        assertThat(status)
                .isEqualTo(
                        new ScopeStatus(
                                ScopeState.OK,
                                ScopeState.OK,
                                ScopeState.OK,
                                ScopeState.OK,
                                ScopeState.OK,
                                ScopeState.OK,
                                ScopeState.OK,
                                ScopeState.OK,
                                ScopeState.OK));
    }

    @Test
    void keepsEmployeeAuthorityOkWhenTheCaseCustomerIsViolated() {
        ScopeStatus status =
                resolver.resolve(
                        activeContext(),
                        "LOAN-AGENT-01",
                        "CUST-9999",
                        Tool.CREDIT_SCORE_READ,
                        Set.of(DataType.CREDIT_SCORE),
                        NOW);

        assertThat(status.employeeAuthority()).isEqualTo(ScopeState.OK);
        assertThat(status.customerScope()).isEqualTo(ScopeState.VIOLATION);
        assertThat(status.toolScope()).isEqualTo(ScopeState.OK);
        assertThat(status.dataScope()).isEqualTo(ScopeState.OK);
    }

    @Test
    void evaluatesEmployeeAuthorityAgainstTheOriginalAuthority() {
        RuntimeFinancialContext context =
                context(
                        authority(Set.of(Tool.INCOME_READ), ALL_DATA),
                        template(ALL_TOOLS, ALL_DATA),
                        financialCase(FinancialCaseStatus.ACTIVE),
                        mandate(ConsumerMandateStatus.ACTIVE, ALL_DATA),
                        passport(TaskPassportStatus.ACTIVE, ALL_TOOLS, ALL_DATA, CURRENT_VERSIONS),
                        CURRENT_VERSIONS);

        ScopeStatus status = resolve(context);

        assertThat(status.employeeAuthority()).isEqualTo(ScopeState.VIOLATION);
        assertThat(status.toolScope()).isEqualTo(ScopeState.OK);
    }

    @Test
    void evaluatesPermissionTemplateAgainstTheOriginalTemplate() {
        RuntimeFinancialContext context =
                context(
                        authority(ALL_TOOLS, ALL_DATA),
                        template(Set.of(Tool.INCOME_READ), ALL_DATA),
                        financialCase(FinancialCaseStatus.ACTIVE),
                        mandate(ConsumerMandateStatus.ACTIVE, ALL_DATA),
                        passport(TaskPassportStatus.ACTIVE, ALL_TOOLS, ALL_DATA, CURRENT_VERSIONS),
                        CURRENT_VERSIONS);

        ScopeStatus status = resolve(context);

        assertThat(status.permissionTemplate()).isEqualTo(ScopeState.VIOLATION);
        assertThat(status.toolScope()).isEqualTo(ScopeState.OK);
    }

    @Test
    void marksExpiredCaseAndRevokedMandateIndependently() {
        RuntimeFinancialContext context =
                context(
                        authority(ALL_TOOLS, ALL_DATA),
                        template(ALL_TOOLS, ALL_DATA),
                        financialCase(FinancialCaseStatus.EXPIRED),
                        mandate(ConsumerMandateStatus.REVOKED, ALL_DATA),
                        passport(TaskPassportStatus.ACTIVE, ALL_TOOLS, ALL_DATA, CURRENT_VERSIONS),
                        CURRENT_VERSIONS);

        ScopeStatus status = resolve(context);

        assertThat(status.caseStatus()).isEqualTo(ScopeState.VIOLATION);
        assertThat(status.mandate()).isEqualTo(ScopeState.VIOLATION);
    }

    @Test
    void marksPassportStaleWhenAnySourceVersionChanged() {
        RuntimeFinancialContext context =
                context(
                        authority(ALL_TOOLS, ALL_DATA),
                        template(ALL_TOOLS, ALL_DATA),
                        financialCase(FinancialCaseStatus.ACTIVE),
                        mandate(ConsumerMandateStatus.ACTIVE, ALL_DATA),
                        passport(
                                TaskPassportStatus.ACTIVE,
                                ALL_TOOLS,
                                ALL_DATA,
                                new SourceVersions(0, 0, 0, 1)),
                        CURRENT_VERSIONS);

        ScopeStatus status = resolve(context);

        assertThat(status.passportStatus()).isEqualTo(ScopeState.VIOLATION);
    }

    @Test
    void usesTheVerifiedGatewayIdentityForAgentBinding() {
        ScopeStatus status =
                resolver.resolve(
                        activeContext(),
                        "SPOOFED-AGENT",
                        "CUST-1001",
                        Tool.CREDIT_SCORE_READ,
                        Set.of(DataType.CREDIT_SCORE),
                        NOW);

        assertThat(status.agentBinding()).isEqualTo(ScopeState.VIOLATION);
    }

    @Test
    void evaluatesToolScopeAgainstTheIssuedPassport() {
        RuntimeFinancialContext context =
                context(
                        authority(ALL_TOOLS, ALL_DATA),
                        template(ALL_TOOLS, ALL_DATA),
                        financialCase(FinancialCaseStatus.ACTIVE),
                        mandate(ConsumerMandateStatus.ACTIVE, ALL_DATA),
                        passport(
                                TaskPassportStatus.ACTIVE,
                                Set.of(Tool.INCOME_READ),
                                ALL_DATA,
                                CURRENT_VERSIONS),
                        CURRENT_VERSIONS);

        ScopeStatus status = resolve(context);

        assertThat(status.employeeAuthority()).isEqualTo(ScopeState.OK);
        assertThat(status.permissionTemplate()).isEqualTo(ScopeState.OK);
        assertThat(status.toolScope()).isEqualTo(ScopeState.VIOLATION);
    }

    @Test
    void evaluatesDataScopeAgainstTheIssuedPassport() {
        RuntimeFinancialContext context =
                context(
                        authority(ALL_TOOLS, ALL_DATA),
                        template(ALL_TOOLS, ALL_DATA),
                        financialCase(FinancialCaseStatus.ACTIVE),
                        mandate(ConsumerMandateStatus.ACTIVE, ALL_DATA),
                        passport(
                                TaskPassportStatus.ACTIVE,
                                ALL_TOOLS,
                                Set.of(DataType.CREDIT_SCORE),
                                CURRENT_VERSIONS),
                        CURRENT_VERSIONS);

        ScopeStatus status =
                resolver.resolve(
                        context,
                        "LOAN-AGENT-01",
                        "CUST-1001",
                        Tool.CREDIT_SCORE_READ,
                        Set.of(DataType.CREDIT_SCORE, DataType.INCOME),
                        NOW);

        assertThat(status.employeeAuthority()).isEqualTo(ScopeState.OK);
        assertThat(status.permissionTemplate()).isEqualTo(ScopeState.OK);
        assertThat(status.mandate()).isEqualTo(ScopeState.OK);
        assertThat(status.dataScope()).isEqualTo(ScopeState.VIOLATION);
    }

    private ScopeStatus resolve(RuntimeFinancialContext context) {
        return resolver.resolve(
                context,
                "LOAN-AGENT-01",
                "CUST-1001",
                Tool.CREDIT_SCORE_READ,
                Set.of(DataType.CREDIT_SCORE),
                NOW);
    }

    private RuntimeFinancialContext activeContext() {
        return context(
                authority(ALL_TOOLS, ALL_DATA),
                template(ALL_TOOLS, ALL_DATA),
                financialCase(FinancialCaseStatus.ACTIVE),
                mandate(ConsumerMandateStatus.ACTIVE, ALL_DATA),
                passport(TaskPassportStatus.ACTIVE, ALL_TOOLS, ALL_DATA, CURRENT_VERSIONS),
                CURRENT_VERSIONS);
    }

    private RuntimeFinancialContext context(
            EmployeeAuthority authority,
            PermissionTemplate template,
            FinancialCase financialCase,
            ConsumerMandate mandate,
            TaskPassport passport,
            SourceVersions currentVersions) {
        return new RuntimeFinancialContext(
                authority, template, financialCase, mandate, passport, currentVersions);
    }

    private EmployeeAuthority authority(Set<Tool> tools, Set<DataType> data) {
        return new EmployeeAuthority(
                "EMP-101", EmployeeAuthorityStatus.ACTIVE, CustomerScope.ALL, tools, data);
    }

    private PermissionTemplate template(Set<Tool> tools, Set<DataType> data) {
        return new PermissionTemplate(
                "LOAN_REVIEW_STANDARD",
                TaskType.LOAN_REVIEW,
                tools,
                data,
                60,
                PermissionTemplateStatus.ACTIVE);
    }

    private FinancialCase financialCase(FinancialCaseStatus status) {
        return new FinancialCase(
                "LOAN-2026-001",
                "EMP-101",
                "CUST-1001",
                TaskType.LOAN_REVIEW,
                "LOAN_REVIEW_STANDARD",
                status,
                NOW.minusSeconds(60),
                NOW.plusSeconds(3600));
    }

    private ConsumerMandate mandate(ConsumerMandateStatus status, Set<DataType> data) {
        return new ConsumerMandate("CUST-1001", TaskType.LOAN_REVIEW, status, data);
    }

    private TaskPassport passport(
            TaskPassportStatus status,
            Set<Tool> tools,
            Set<DataType> data,
            SourceVersions sourceVersions) {
        return new TaskPassport(
                "PASS-001",
                "LOAN-AGENT-01",
                "EMP-101",
                "LOAN-2026-001",
                "CUST-1001",
                TaskType.LOAN_REVIEW,
                tools,
                data,
                status,
                NOW.minusSeconds(60),
                NOW.plusSeconds(3600),
                sourceVersions);
    }
}
