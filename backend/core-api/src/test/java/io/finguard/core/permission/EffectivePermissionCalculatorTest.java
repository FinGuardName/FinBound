package io.finguard.core.permission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.EnumSet;
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
import io.finguard.core.domain.TaskType;
import io.finguard.core.domain.Tool;

/**
 * 권한을 좁히는 계산. {@code docs/01-feature-spec.md} F05.
 *
 * <pre>
 * Employee Authority ∩ Permission Template ∩ Financial Case ∩ Consumer Mandate
 *     → Agent Effective Permission
 * </pre>
 */
class EffectivePermissionCalculatorTest {

    private static final Instant NOW = Instant.parse("2026-08-17T05:00:00Z");
    private static final Instant EXPIRES = Instant.parse("2026-08-17T06:00:00Z");

    private final EffectivePermissionCalculator calculator = new EffectivePermissionCalculator();

    @Test
    void toolsAreTheIntersectionOfAuthorityAndTemplate() {
        EmployeeAuthority authority =
                authority(EnumSet.allOf(Tool.class), EnumSet.allOf(DataType.class));
        PermissionTemplate template =
                template(
                        EnumSet.of(Tool.CREDIT_SCORE_READ, Tool.INCOME_READ),
                        EnumSet.allOf(DataType.class));

        EffectivePermission permission =
                calculator.calculate(authority, template, activeCase(), mandate(EnumSet.allOf(DataType.class)), NOW);

        assertThat(permission.allowedTools())
                .containsExactlyInAnyOrder(Tool.CREDIT_SCORE_READ, Tool.INCOME_READ);
    }

    @Test
    void dataIsNarrowedByTheConsumerMandateToo() {
        EmployeeAuthority authority =
                authority(EnumSet.allOf(Tool.class), EnumSet.allOf(DataType.class));
        PermissionTemplate template =
                template(EnumSet.allOf(Tool.class), EnumSet.allOf(DataType.class));

        // 소비자가 CREDIT_SCORE 만 동의했다면 직원 권한과 업무 표준이 아무리 넓어도 거기까지다.
        EffectivePermission permission =
                calculator.calculate(
                        authority, template, activeCase(), mandate(EnumSet.of(DataType.CREDIT_SCORE)), NOW);

        assertThat(permission.allowedData()).containsExactly(DataType.CREDIT_SCORE);
    }

    @Test
    void neverExceedsEmployeeAuthority() {
        // AGENTS.md 의 핵심 불변식. 직원이 못 하는 일을 Agent가 할 수 없다.
        EmployeeAuthority narrowAuthority =
                authority(EnumSet.of(Tool.CREDIT_SCORE_READ), EnumSet.of(DataType.CREDIT_SCORE));
        PermissionTemplate wideTemplate =
                template(EnumSet.allOf(Tool.class), EnumSet.allOf(DataType.class));

        EffectivePermission permission =
                calculator.calculate(
                        narrowAuthority, wideTemplate, activeCase(), mandate(EnumSet.allOf(DataType.class)), NOW);

        assertThat(narrowAuthority.getAllowedTools()).containsAll(permission.allowedTools());
        assertThat(narrowAuthority.getAllowedData()).containsAll(permission.allowedData());
        assertThat(permission.allowedTools()).containsExactly(Tool.CREDIT_SCORE_READ);
        assertThat(permission.allowedData()).containsExactly(DataType.CREDIT_SCORE);
    }

    @Test
    void emptyIntersectionIsAValidResultNotAnError() {
        // 교집합이 비는 것은 계산 실패가 아니다. 아무것도 못 하는 Passport 는 정상적으로 존재할 수 있고,
        // 그 경우 모든 Tool Call 이 toolScope 위반으로 막힌다.
        EmployeeAuthority authority =
                authority(EnumSet.of(Tool.CREDIT_SCORE_READ), EnumSet.of(DataType.CREDIT_SCORE));
        PermissionTemplate template =
                template(EnumSet.of(Tool.DEBT_READ), EnumSet.of(DataType.DEBT));

        EffectivePermission permission =
                calculator.calculate(
                        authority, template, activeCase(), mandate(EnumSet.allOf(DataType.class)), NOW);

        assertThat(permission.allowedTools()).isEmpty();
        assertThat(permission.allowedData()).isEmpty();
    }

    @Test
    void removesToolsWhoseDataTheConsumerDidNotAllow() {
        // 소비자가 CREDIT_SCORE 만 동의했다면 INCOME_READ 는 남아 있으면 안 된다.
        // 남겨두면 거부된 Data 를 그 Tool 이 우회로 읽고, requestedData 를 비워 보내면
        // dataScope 검사도 지나간다.
        EffectivePermission permission =
                calculator.calculate(
                        wideAuthority(),
                        wideTemplate(),
                        activeCase(),
                        mandate(EnumSet.of(DataType.CREDIT_SCORE)),
                        NOW);

        assertThat(permission.allowedTools()).containsExactly(Tool.CREDIT_SCORE_READ);
        assertThat(permission.allowedData()).containsExactly(DataType.CREDIT_SCORE);
    }

    @Test
    void refusesWhenTheAuthorityBelongsToAnotherEmployee() {
        EmployeeAuthority otherEmployee =
                new EmployeeAuthority(
                        "EMP-999",
                        EmployeeAuthorityStatus.ACTIVE,
                        CustomerScope.ALL,
                        EnumSet.allOf(Tool.class),
                        EnumSet.allOf(DataType.class));

        // Case 는 EMP-101 의 것이다. 다른 직원의 권한으로 계산하면 안 된다.
        assertThatThrownBy(
                        () ->
                                calculator.calculate(
                                        otherEmployee, wideTemplate(), activeCase(), wideMandate(), NOW))
                .isInstanceOf(PermissionNotIssuableException.class)
                .extracting("reasonCode")
                .isEqualTo("CONTEXT_NOT_FOUND");
    }

    @Test
    void refusesWhenTheTemplateIsNotTheOneTheCaseWasOpenedWith() {
        PermissionTemplate otherTemplate =
                new PermissionTemplate(
                        "SOME_OTHER_TEMPLATE",
                        TaskType.LOAN_REVIEW,
                        EnumSet.allOf(Tool.class),
                        EnumSet.allOf(DataType.class),
                        60,
                        PermissionTemplateStatus.ACTIVE);

        assertThatThrownBy(
                        () ->
                                calculator.calculate(
                                        wideAuthority(), otherTemplate, activeCase(), wideMandate(), NOW))
                .isInstanceOf(PermissionNotIssuableException.class)
                .extracting("reasonCode")
                .isEqualTo("CONTEXT_NOT_FOUND");
    }

    @Test
    void refusesWhenEmployeeAuthorityIsInactive() {
        EmployeeAuthority inactive =
                new EmployeeAuthority(
                        "EMP-101",
                        EmployeeAuthorityStatus.INACTIVE,
                        CustomerScope.ALL,
                        EnumSet.allOf(Tool.class),
                        EnumSet.allOf(DataType.class));

        // F01 — 직원 권한이 비활성이면 AgentRun 을 시작하지 않는다.
        assertThatThrownBy(
                        () ->
                                calculator.calculate(
                                        inactive, wideTemplate(), activeCase(), wideMandate(), NOW))
                .isInstanceOf(PermissionNotIssuableException.class)
                .extracting("reasonCode")
                .isEqualTo("EMPLOYEE_AUTHORITY_INACTIVE");
    }

    @Test
    void refusesWhenPermissionTemplateIsInactive() {
        PermissionTemplate inactive =
                new PermissionTemplate(
                        "LOAN_REVIEW_STANDARD",
                        TaskType.LOAN_REVIEW,
                        EnumSet.allOf(Tool.class),
                        EnumSet.allOf(DataType.class),
                        60,
                        PermissionTemplateStatus.INACTIVE);

        assertThatThrownBy(
                        () ->
                                calculator.calculate(
                                        wideAuthority(), inactive, activeCase(), wideMandate(), NOW))
                .isInstanceOf(PermissionNotIssuableException.class)
                .extracting("reasonCode")
                .isEqualTo("PERMISSION_TEMPLATE_INACTIVE");
    }

    @Test
    void refusesWhenCaseIsNotActive() {
        FinancialCase cancelled = financialCase(FinancialCaseStatus.CANCELLED, EXPIRES);

        assertThatThrownBy(
                        () ->
                                calculator.calculate(
                                        wideAuthority(), wideTemplate(), cancelled, wideMandate(), NOW))
                .isInstanceOf(PermissionNotIssuableException.class)
                .extracting("reasonCode")
                .isEqualTo("CASE_INACTIVE");
    }

    @Test
    void reportsExpiryAsExpiryEvenWhenTheStatusSaysSo() {
        // 상태가 EXPIRED 인데 CASE_INACTIVE 로 기록하면 감사 기록이 사실과 달라진다.
        // "권한을 회수당했다"와 "시간이 지났다"는 다른 사건이다.
        FinancialCase expired = financialCase(FinancialCaseStatus.EXPIRED, EXPIRES);

        assertThatThrownBy(
                        () ->
                                calculator.calculate(
                                        wideAuthority(), wideTemplate(), expired, wideMandate(), NOW))
                .isInstanceOf(PermissionNotIssuableException.class)
                .extracting("reasonCode")
                .isEqualTo("CASE_EXPIRED");
    }

    @Test
    void refusesWhenCaseHasExpired() {
        // 상태는 ACTIVE 인데 시각이 지난 경우다. 만료와 비활성은 다른 Reason Code 다 (docs/06 §20).
        assertThatThrownBy(
                        () ->
                                calculator.calculate(
                                        wideAuthority(),
                                        wideTemplate(),
                                        activeCase(),
                                        wideMandate(),
                                        EXPIRES.plusSeconds(1)))
                .isInstanceOf(PermissionNotIssuableException.class)
                .extracting("reasonCode")
                .isEqualTo("CASE_EXPIRED");
    }

    @Test
    void refusesWhenMandateIsNotActive() {
        ConsumerMandate revoked =
                new ConsumerMandate(
                        "CUST-1001",
                        TaskType.LOAN_REVIEW,
                        ConsumerMandateStatus.REVOKED,
                        EnumSet.allOf(DataType.class));

        assertThatThrownBy(
                        () ->
                                calculator.calculate(
                                        wideAuthority(), wideTemplate(), activeCase(), revoked, NOW))
                .isInstanceOf(PermissionNotIssuableException.class)
                .extracting("reasonCode")
                .isEqualTo("MANDATE_INACTIVE");
    }

    @Test
    void refusesWhenMandateBelongsToAnotherConsumer() {
        // F02 — Mandate 는 현재 Case 의 consumerId + purpose 와 일치해야 한다.
        // 엉뚱한 소비자의 동의로 권한을 넓히는 것을 막는다.
        ConsumerMandate otherConsumer =
                new ConsumerMandate(
                        "CUST-9999",
                        TaskType.LOAN_REVIEW,
                        ConsumerMandateStatus.ACTIVE,
                        EnumSet.allOf(DataType.class));

        assertThatThrownBy(
                        () ->
                                calculator.calculate(
                                        wideAuthority(), wideTemplate(), activeCase(), otherConsumer, NOW))
                .isInstanceOf(PermissionNotIssuableException.class)
                .extracting("reasonCode")
                .isEqualTo("MANDATE_NOT_FOUND");
    }

    private EmployeeAuthority wideAuthority() {
        return authority(EnumSet.allOf(Tool.class), EnumSet.allOf(DataType.class));
    }

    private PermissionTemplate wideTemplate() {
        return template(EnumSet.allOf(Tool.class), EnumSet.allOf(DataType.class));
    }

    private ConsumerMandate wideMandate() {
        return mandate(EnumSet.allOf(DataType.class));
    }

    private FinancialCase financialCase(FinancialCaseStatus status, Instant expiresAt) {
        return new FinancialCase(
                "LOAN-2026-001",
                "EMP-101",
                "CUST-1001",
                TaskType.LOAN_REVIEW,
                "LOAN_REVIEW_STANDARD",
                status,
                NOW,
                expiresAt);
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

    private FinancialCase activeCase() {
        return new FinancialCase(
                "LOAN-2026-001",
                "EMP-101",
                "CUST-1001",
                TaskType.LOAN_REVIEW,
                "LOAN_REVIEW_STANDARD",
                FinancialCaseStatus.ACTIVE,
                NOW,
                EXPIRES);
    }

    private ConsumerMandate mandate(Set<DataType> data) {
        return new ConsumerMandate(
                "CUST-1001", TaskType.LOAN_REVIEW, ConsumerMandateStatus.ACTIVE, data);
    }
}
