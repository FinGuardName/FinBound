package io.finguard.core.context;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.finguard.core.context.ContextResolveResponse.PromptRiskView;
import io.finguard.core.context.ContextResolveResponse.References;
import io.finguard.core.domain.AgentRun;
import io.finguard.core.domain.AgentRunStatus;
import io.finguard.core.domain.ConsumerMandate;
import io.finguard.core.domain.EmployeeAuthority;
import io.finguard.core.domain.FinancialCase;
import io.finguard.core.domain.PermissionTemplate;
import io.finguard.core.domain.PromptRiskEvaluationStatus;
import io.finguard.core.domain.PromptRiskSnapshot;
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

    private static final Logger log = LoggerFactory.getLogger(ContextResolveService.class);

    /**
     * 가장 나쁜 Prompt Risk 스냅샷이 앞에 오는 순서.
     *
     * <p>탐지된 것 → 미검사인 것 → 점수가 높은 것. 하나라도 {@code NOT_EVALUATED}이면 그것을 고른다.
     * "검사하지 않았음"을 "검사했고 음성"으로 보고하면 감사 기록이 거짓이 된다({@code docs/04} §7).
     */
    private static final Comparator<PromptRiskSnapshot> WORST_FIRST =
            Comparator.comparingInt((PromptRiskSnapshot snapshot) -> snapshot.isDetected() ? 0 : 1)
                    .thenComparingInt(
                            snapshot ->
                                    snapshot.getEvaluationStatus()
                                                    == PromptRiskEvaluationStatus.NOT_EVALUATED
                                            ? 0
                                            : 1)
                    .thenComparing(PromptRiskSnapshot::getPromptRisk, Comparator.reverseOrder());

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
        // 본문의 verifiedAgentId 는 계약이 요구하는 필드지만 권한 근거가 아니다(docs/04 §1.4).
        // 판정에는 헤더 값만 쓴다. 다만 둘이 다르면 Gateway 버그이거나 조작 시도이므로
        // 조용히 지나가지 않게 남긴다. 거부하려면 전용 Reason Code 가 필요해 팀 합의 사항이다.
        if (!trustedVerifiedAgentId.equals(request.verifiedAgentId())) {
            log.warn(
                    "Request body verifiedAgentId disagrees with the verified header; using the header."
                            + " requestId={} headerAgentId={} bodyAgentId={}",
                    request.requestId(),
                    trustedVerifiedAgentId,
                    request.verifiedAgentId());
        }

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

        PromptRiskSnapshot promptRisk = resolvePromptRisk(agentRun);

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

    /**
     * AgentRun에 등록된 <strong>모든</strong> 입력의 Prompt Risk를 보고 가장 나쁜 것을 고른다.
     *
     * <p>마지막 입력만 보면 앞서 들어온 문서의 주입이 무시된다. 새 Document가 추가될 때마다 검사한다는
     * 규칙({@code 개발범위} §2.2)이 성립하려면 판정도 전체를 봐야 한다.
     *
     * <p>고르는 순서는 fail-closed다 — 탐지된 것, 그다음 미검사인 것, 그다음 점수가 높은 것.
     * 하나라도 {@code NOT_EVALUATED}이면 "검사했고 음성"이라고 보고할 수 없다({@code docs/04} §7).
     */
    private PromptRiskSnapshot resolvePromptRisk(AgentRun agentRun) {
        List<String> inputRefs = agentRun.getInputRefs();
        if (inputRefs.isEmpty()) {
            throw ContextLookupException.contextNotFound();
        }
        return inputRefs.stream()
                .map(
                        inputRef ->
                                required(
                                        securedInputs.findByInputRefAndAgentRunId(
                                                inputRef, agentRun.getAgentRunId())))
                .map(
                        input ->
                                required(
                                        promptRiskSnapshots.findByInputHashAndModelVersion(
                                                input.getInputHash(), PromptRiskModel.CURRENT_VERSION)))
                .min(WORST_FIRST)
                .orElseThrow(ContextLookupException::contextNotFound);
    }

    private void verifyRelationships(
            AgentRun agentRun,
            TaskPassport passport,
            FinancialCase financialCase,
            EmployeeAuthority authority,
            PermissionTemplate template,
            ConsumerMandate mandate) {
        // 끝난 실행은 더 이상 Tool Call의 근거가 될 수 없다. 상태를 보지 않으면 COMPLETED·FAILED
        // AgentRun 으로도 모든 Scope 가 OK 로 나온다.
        boolean runIsInFlight = agentRun.getStatus() == AgentRunStatus.RUNNING;
        boolean runMatchesPassport =
                runIsInFlight
                        && agentRun.getPassportId().equals(passport.getPassportId())
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
            // 응답의 reasonCode 는 계약 어휘인 CONTEXT_NOT_FOUND 그대로 나간다. 다만 "레코드가 없다"와
            // "레코드는 있는데 서로 안 맞는다"는 전혀 다른 사건이고, 후자는 남의 Passport 로 자기
            // AgentRun 을 엮으려 한 흔적일 수 있다. 그 신호를 로그에서라도 구분한다.
            // 전용 Reason Code 를 두려면 docs/04·docs/06 변경이라 팀 합의가 필요하다.
            log.warn(
                    "Runtime context relationships do not agree."
                            + " agentRunId={} passportId={} caseId={}"
                            + " runInFlight={} runMatchesPassport={} passportMatchesCase={} sourcesMatchCase={}",
                    agentRun.getAgentRunId(),
                    passport.getPassportId(),
                    financialCase.getCaseId(),
                    runIsInFlight,
                    runMatchesPassport,
                    passportMatchesCase,
                    sourcesMatchCase);
            throw ContextLookupException.contextNotFound();
        }
    }

    private <T> T required(Optional<T> value) {
        return value.orElseThrow(ContextLookupException::contextNotFound);
    }
}
