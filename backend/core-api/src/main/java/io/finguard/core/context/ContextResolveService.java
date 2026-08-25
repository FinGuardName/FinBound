package io.finguard.core.context;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.finguard.core.context.ContextResolveResponse.PromptRiskView;
import io.finguard.core.context.ContextResolveResponse.References;
import io.finguard.core.domain.AgentRun;
import io.finguard.core.domain.ConsumerMandate;
import io.finguard.core.domain.EmployeeAuthority;
import io.finguard.core.domain.FinancialCase;
import io.finguard.core.domain.PermissionTemplate;
import io.finguard.core.domain.PromptRiskSnapshot;
import io.finguard.core.domain.SecuredAgentInput;
import io.finguard.core.domain.SourceVersions;
import io.finguard.core.domain.TaskPassport;
import io.finguard.core.repository.AgentRunRepository;
import io.finguard.core.repository.ConsumerMandateRepository;
import io.finguard.core.repository.EmployeeAuthorityRepository;
import io.finguard.core.repository.FinancialCaseRepository;
import io.finguard.core.repository.PermissionTemplateRepository;
import io.finguard.core.repository.PromptRiskSnapshotRepository;
import io.finguard.core.repository.SecuredAgentInputRepository;
import io.finguard.core.repository.TaskPassportRepository;
import io.finguard.core.risk.PromptRiskModel;

/** 신뢰 가능한 Runtime Context를 조립하고 9개 Scope 상태를 계산한다. */
@Service
public class ContextResolveService {

    private final TaskPassportRepository taskPassports;
    private final AgentRunRepository agentRuns;
    private final FinancialCaseRepository financialCases;
    private final EmployeeAuthorityRepository employeeAuthorities;
    private final PermissionTemplateRepository permissionTemplates;
    private final ConsumerMandateRepository consumerMandates;
    private final SecuredAgentInputRepository securedInputs;
    private final PromptRiskSnapshotRepository promptRiskSnapshots;
    private final FinancialContextResolver resolver;
    private final Clock clock;

    public ContextResolveService(
            TaskPassportRepository taskPassports,
            AgentRunRepository agentRuns,
            FinancialCaseRepository financialCases,
            EmployeeAuthorityRepository employeeAuthorities,
            PermissionTemplateRepository permissionTemplates,
            ConsumerMandateRepository consumerMandates,
            SecuredAgentInputRepository securedInputs,
            PromptRiskSnapshotRepository promptRiskSnapshots,
            FinancialContextResolver resolver,
            Clock clock) {
        this.taskPassports = taskPassports;
        this.agentRuns = agentRuns;
        this.financialCases = financialCases;
        this.employeeAuthorities = employeeAuthorities;
        this.permissionTemplates = permissionTemplates;
        this.consumerMandates = consumerMandates;
        this.securedInputs = securedInputs;
        this.promptRiskSnapshots = promptRiskSnapshots;
        this.resolver = resolver;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ContextResolveResponse resolve(
            ContextResolveRequest request, String trustedVerifiedAgentId) {
        TaskPassport passport =
                taskPassports
                        .findById(request.passportId())
                        .orElseThrow(ContextLookupException::passportNotFound);
        AgentRun agentRun = required(agentRuns.findById(request.agentRunId()));
        FinancialCase financialCase = required(financialCases.findById(passport.getCaseId()));
        EmployeeAuthority authority =
                required(employeeAuthorities.findById(passport.getEmployeeId()));
        PermissionTemplate template =
                required(permissionTemplates.findById(financialCase.getTemplateId()));
        ConsumerMandate mandate =
                required(
                        consumerMandates.findByConsumerIdAndPurpose(
                                passport.getConsumerId(), passport.getTaskType()));

        verifyRelationships(agentRun, passport, financialCase, authority, template, mandate);

        SecuredAgentInput securedInput = findCurrentInput(agentRun);
        PromptRiskSnapshot promptRisk =
                required(
                        promptRiskSnapshots.findByInputHashAndModelVersion(
                                securedInput.getInputHash(), PromptRiskModel.CURRENT_VERSION));

        RuntimeFinancialContext context =
                new RuntimeFinancialContext(
                        authority,
                        template,
                        financialCase,
                        mandate,
                        passport,
                        new SourceVersions(
                                authority.getVersion(),
                                template.getVersion(),
                                financialCase.getVersion(),
                                mandate.getVersion()));
        ScopeStatus scopeStatus =
                resolver.resolve(
                        context,
                        trustedVerifiedAgentId,
                        request.targetConsumerId(),
                        request.requestedTool(),
                        request.requestedData(),
                        clock.instant());

        return new ContextResolveResponse(
                request.requestId(),
                new References(
                        passport.getEmployeeId(), passport.getCaseId(), passport.getPassportId()),
                scopeStatus,
                new PromptRiskView(
                        promptRisk.getEvaluationStatus(),
                        promptRisk.getPromptRisk(),
                        promptRisk.isDetected(),
                        promptRisk.getInputHash(),
                        promptRisk.getModelVersion()));
    }

    private SecuredAgentInput findCurrentInput(AgentRun agentRun) {
        List<String> inputRefs = agentRun.getInputRefs();
        if (inputRefs.isEmpty()) {
            throw ContextLookupException.contextNotFound();
        }
        String currentInputRef = inputRefs.get(inputRefs.size() - 1);
        return required(
                securedInputs.findByInputRefAndAgentRunId(
                        currentInputRef, agentRun.getAgentRunId()));
    }

    private void verifyRelationships(
            AgentRun agentRun,
            TaskPassport passport,
            FinancialCase financialCase,
            EmployeeAuthority authority,
            PermissionTemplate template,
            ConsumerMandate mandate) {
        boolean runMatchesPassport =
                agentRun.getPassportId().equals(passport.getPassportId())
                        && agentRun.getAgentId().equals(passport.getAgentId())
                        && agentRun.getEmployeeId().equals(passport.getEmployeeId())
                        && agentRun.getCaseId().equals(passport.getCaseId());
        boolean passportMatchesCase =
                passport.getEmployeeId().equals(financialCase.getEmployeeId())
                        && passport.getConsumerId().equals(financialCase.getConsumerId())
                        && passport.getTaskType() == financialCase.getTaskType();
        boolean sourcesMatchCase =
                authority.getEmployeeId().equals(passport.getEmployeeId())
                        && template.getTemplateId().equals(financialCase.getTemplateId())
                        && template.getTaskType() == financialCase.getTaskType()
                        && mandate.getConsumerId().equals(passport.getConsumerId())
                        && mandate.getPurpose() == passport.getTaskType();
        if (!(runMatchesPassport && passportMatchesCase && sourcesMatchCase)) {
            throw ContextLookupException.contextNotFound();
        }
    }

    private <T> T required(Optional<T> value) {
        return value.orElseThrow(ContextLookupException::contextNotFound);
    }
}
