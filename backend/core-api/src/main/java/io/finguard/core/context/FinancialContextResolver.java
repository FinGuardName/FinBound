package io.finguard.core.context;

import java.time.Instant;
import java.util.Set;

import io.finguard.core.domain.ConsumerMandate;
import io.finguard.core.domain.CustomerScope;
import io.finguard.core.domain.DataType;
import io.finguard.core.domain.EmployeeAuthority;
import io.finguard.core.domain.FinancialCase;
import io.finguard.core.domain.PermissionTemplate;
import io.finguard.core.domain.TaskPassport;
import io.finguard.core.domain.Tool;

/**
 * Runtime 요청이 각 권한과 Context 범위 안에 있는지 계산하는 Single Source of Truth.
 *
 * <p>최종 ALLOW/BLOCK은 결정하지 않는다. Rego는 여기서 계산한 {@link ScopeStatus}만 소비한다.
 */
public class FinancialContextResolver {

    public ScopeStatus resolve(
            RuntimeFinancialContext context,
            String verifiedAgentId,
            String targetConsumerId,
            Tool requestedTool,
            Set<DataType> requestedData,
            Instant now) {
        EmployeeAuthority authority = context.authority();
        PermissionTemplate template = context.template();
        FinancialCase financialCase = context.financialCase();
        ConsumerMandate mandate = context.mandate();
        TaskPassport passport = context.passport();

        return new ScopeStatus(
                stateOf(
                        authority.isActive()
                                && authority.getAllowedCustomerScope() == CustomerScope.ALL
                                && authority.getAllowedTools().contains(requestedTool)
                                && authority.getAllowedData().containsAll(requestedData)),
                stateOf(
                        template.isActive()
                                && template.getTaskType() == passport.getTaskType()
                                && template.getAllowedTools().contains(requestedTool)
                                && template.getAllowedData().containsAll(requestedData)),
                stateOf(financialCase.isUsableAt(now)),
                stateOf(
                        mandate.isActive()
                                && mandate.getConsumerId().equals(passport.getConsumerId())
                                && mandate.getPurpose() == passport.getTaskType()
                                && mandate.getAllowedData().containsAll(requestedData)),
                stateOf(
                        passport.isUsableAt(now)
                                && passport
                                        .getSourceVersions()
                                        .matches(context.currentSourceVersions())),
                stateOf(passport.getAgentId().equals(verifiedAgentId)),
                stateOf(
                        targetConsumerId.equals(passport.getConsumerId())
                                && passport.getConsumerId().equals(financialCase.getConsumerId())),
                stateOf(passport.getAllowedTools().contains(requestedTool)),
                stateOf(passport.getAllowedData().containsAll(requestedData)));
    }

    private ScopeState stateOf(boolean inScope) {
        return inScope ? ScopeState.OK : ScopeState.VIOLATION;
    }
}
