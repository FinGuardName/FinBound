package io.finguard.core.permission;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import io.finguard.core.domain.ConsumerMandate;
import io.finguard.core.domain.DataType;
import io.finguard.core.domain.EmployeeAuthority;
import io.finguard.core.domain.FinancialCase;
import io.finguard.core.domain.FinancialCaseStatus;
import io.finguard.core.domain.PermissionTemplate;
import io.finguard.core.domain.Tool;

/**
 * 네 원천의 교집합으로 Agent Effective Permission을 계산한다. {@code docs/01-feature-spec.md} F05.
 *
 * <pre>
 * Employee Authority ∩ Permission Template ∩ Financial Case ∩ Consumer Mandate
 * </pre>
 *
 * <p>Tool 은 직원 권한과 업무 표준의 교집합이다. Consumer Mandate 는 Data 범위에만 관여한다 —
 * 소비자가 동의하는 대상은 자기 정보이지 직원이 어떤 도구를 쓰는지가 아니다
 * ({@code docs/01-feature-spec.md} F02: "허용한 Data 범위").
 *
 * <p>Financial Case 는 Tool/Data 를 좁히지 않는다. Case 가 정하는 것은 <strong>대상 고객</strong>이고,
 * 그 제약은 Runtime 에 {@code customerScope} 로 판정된다.
 *
 * <p>교집합이 비는 것은 실패가 아니다. 아무것도 허용되지 않는 Passport 도 정상적으로 존재할 수 있다.
 */
public class EffectivePermissionCalculator {

    public EffectivePermission calculate(
            EmployeeAuthority authority,
            PermissionTemplate template,
            FinancialCase financialCase,
            ConsumerMandate mandate,
            Instant now) {
        verifyIssuable(authority, template, financialCase, mandate, now);

        Set<Tool> tools = EnumSet.noneOf(Tool.class);
        tools.addAll(authority.getAllowedTools());
        tools.retainAll(template.getAllowedTools());

        Set<DataType> data = EnumSet.noneOf(DataType.class);
        data.addAll(authority.getAllowedData());
        data.retainAll(template.getAllowedData());
        data.retainAll(mandate.getAllowedData());

        // Tool은 자기가 읽는 Data를 안다. 읽을 권한이 없는 Data를 요구하는 Tool은 남겨두지 않는다.
        // 남겨두면 소비자가 거부한 Data를 그 Tool 이 우회로 읽게 되고, 요청자가 requestedData 를
        // 비워 보내면 dataScope 검사도 지나간다.
        tools.removeIf(tool -> !data.contains(tool.requiredData()));

        return new EffectivePermission(
                Collections.unmodifiableSet(tools), Collections.unmodifiableSet(data));
    }

    /**
     * 네 원천이 모두 살아 있고 서로 맞물리는지 확인한다.
     *
     * <p>Reason Code 는 {@code docs/06-common-conventions.md} §20 의 어휘다. 비활성과 만료를 다른
     * 코드로 구분한다 — 감사 기록에서 "권한을 회수당했다"와 "시간이 지났다"는 다른 사실이다.
     */
    private void verifyIssuable(
            EmployeeAuthority authority,
            PermissionTemplate template,
            FinancialCase financialCase,
            ConsumerMandate mandate,
            Instant now) {
        // 넘어온 네 개가 실제로 한 업무의 것인지 먼저 본다.
        // 이 검사가 없으면 무관한 Authority·Template·Case 조합으로도 계산이 나온다.
        // 지금 발급 경로는 일관되게 넘기지만, Runtime Resolver가 이 계산기를 재사용하는 순간
        // 요청자가 고른 조합을 그대로 믿게 된다.
        if (!authority.getEmployeeId().equals(financialCase.getEmployeeId())) {
            throw new PermissionNotIssuableException(
                    "CONTEXT_NOT_FOUND",
                    "authorityEmployeeId=" + authority.getEmployeeId()
                            + " caseEmployeeId=" + financialCase.getEmployeeId());
        }
        if (!template.getTemplateId().equals(financialCase.getTemplateId())
                || template.getTaskType() != financialCase.getTaskType()) {
            throw new PermissionNotIssuableException(
                    "CONTEXT_NOT_FOUND",
                    "templateId=" + template.getTemplateId()
                            + " caseTemplateId=" + financialCase.getTemplateId());
        }
        if (!authority.isActive()) {
            throw new PermissionNotIssuableException(
                    "EMPLOYEE_AUTHORITY_INACTIVE", "employeeId=" + authority.getEmployeeId());
        }
        if (!template.isActive()) {
            throw new PermissionNotIssuableException(
                    "PERMISSION_TEMPLATE_INACTIVE", "templateId=" + template.getTemplateId());
        }
        // 만료를 먼저 본다. 상태가 EXPIRED 인 Case 를 CASE_INACTIVE 로 기록하면 감사 기록이
        // 사실과 달라진다 — "권한을 회수당했다"와 "시간이 지났다"는 다른 사건이다.
        if (financialCase.getStatus() == FinancialCaseStatus.EXPIRED
                || !now.isBefore(financialCase.getExpiresAt())) {
            throw new PermissionNotIssuableException(
                    "CASE_EXPIRED", "caseId=" + financialCase.getCaseId());
        }
        if (financialCase.getStatus() != FinancialCaseStatus.ACTIVE) {
            throw new PermissionNotIssuableException(
                    "CASE_INACTIVE", "caseId=" + financialCase.getCaseId());
        }
        // F02 — Mandate 는 현재 Case 의 consumerId + purpose 와 일치해야 한다.
        // 엉뚱한 소비자의 동의를 끌어와 권한을 넓히는 경로를 막는다.
        if (!mandate.getConsumerId().equals(financialCase.getConsumerId())
                || mandate.getPurpose() != financialCase.getTaskType()) {
            throw new PermissionNotIssuableException(
                    "MANDATE_NOT_FOUND",
                    "caseConsumerId=" + financialCase.getConsumerId()
                            + " casePurpose=" + financialCase.getTaskType());
        }
        if (!mandate.isActive()) {
            throw new PermissionNotIssuableException(
                    "MANDATE_INACTIVE", "consumerId=" + mandate.getConsumerId());
        }
    }
}
