package io.finguard.core.agentrun;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.finguard.core.domain.AgentRun;
import io.finguard.core.domain.AgentRunStatus;
import io.finguard.core.domain.ConsumerMandate;
import io.finguard.core.domain.EmployeeAuthority;
import io.finguard.core.domain.FinancialCase;
import io.finguard.core.domain.FinancialCaseStatus;
import io.finguard.core.domain.PermissionTemplate;
import io.finguard.core.domain.PromptRiskSnapshot;
import io.finguard.core.domain.SecuredAgentInput;
import io.finguard.core.domain.SourceVersions;
import io.finguard.core.domain.TaskPassport;
import io.finguard.core.domain.TaskPassportStatus;
import io.finguard.core.domain.TaskType;
import io.finguard.core.permission.EffectivePermission;
import io.finguard.core.permission.EffectivePermissionCalculator;
import io.finguard.core.permission.PermissionNotIssuableException;
import io.finguard.core.repository.AgentRunRepository;
import io.finguard.core.repository.ConsumerMandateRepository;
import io.finguard.core.repository.EmployeeAuthorityRepository;
import io.finguard.core.repository.FinancialCaseRepository;
import io.finguard.core.repository.PermissionTemplateRepository;
import io.finguard.core.repository.PromptRiskSnapshotRepository;
import io.finguard.core.repository.SecuredAgentInputRepository;
import io.finguard.core.repository.TaskPassportRepository;
import io.finguard.core.risk.PromptRiskModel;

/**
 * AgentRun을 시작하고 Task Passport를 발급한다. {@code docs/04-api-contract.md} §3.
 *
 * <p>처리 순서는 계약이 정한 그대로다.
 *
 * <pre>
 * Employee Authority / Permission Template / Mandate 조회
 *   → Financial Case 생성
 *   → Effective Permission 계산
 *   → Task Passport 저장
 *   → Secured Input 저장 + inputHash
 *   → PromptRiskSnapshot 저장
 *   → AgentRun RUNNING
 * </pre>
 *
 * <p>권한 계산이 거부하면 아무것도 저장하지 않는다. 트랜잭션 하나로 묶어 부분 상태를 남기지 않는다 —
 * Passport 없이 떠 있는 AgentRun이나 그 반대가 생기면 Runtime 판정이 성립하지 않는다.
 */
@Service
public class AgentRunService {

    private final EmployeeAuthorityRepository employeeAuthorities;
    private final PermissionTemplateRepository permissionTemplates;
    private final ConsumerMandateRepository consumerMandates;
    private final FinancialCaseRepository financialCases;
    private final TaskPassportRepository taskPassports;
    private final AgentRunRepository agentRuns;
    private final SecuredAgentInputRepository securedInputs;
    private final PromptRiskSnapshotRepository promptRiskSnapshots;
    private final EffectivePermissionCalculator calculator;
    private final Clock clock;

    public AgentRunService(
            EmployeeAuthorityRepository employeeAuthorities,
            PermissionTemplateRepository permissionTemplates,
            ConsumerMandateRepository consumerMandates,
            FinancialCaseRepository financialCases,
            TaskPassportRepository taskPassports,
            AgentRunRepository agentRuns,
            SecuredAgentInputRepository securedInputs,
            PromptRiskSnapshotRepository promptRiskSnapshots,
            EffectivePermissionCalculator calculator,
            Clock clock) {
        this.employeeAuthorities = employeeAuthorities;
        this.permissionTemplates = permissionTemplates;
        this.consumerMandates = consumerMandates;
        this.financialCases = financialCases;
        this.taskPassports = taskPassports;
        this.agentRuns = agentRuns;
        this.securedInputs = securedInputs;
        this.promptRiskSnapshots = promptRiskSnapshots;
        this.calculator = calculator;
        this.clock = clock;
    }

    @Transactional
    public AgentRunStarted start(
            String employeeId, String consumerId, TaskType taskType, String inputText) {
        Instant now = clock.instant();

        EmployeeAuthority authority =
                employeeAuthorities
                        .findById(employeeId)
                        .orElseThrow(
                                () ->
                                        new PermissionNotIssuableException(
                                                "CONTEXT_NOT_FOUND", "employeeAuthority employeeId=" + employeeId));

        PermissionTemplate template =
                permissionTemplates
                        .findFirstByTaskTypeOrderByTemplateIdAsc(taskType)
                        .orElseThrow(
                                () ->
                                        new PermissionNotIssuableException(
                                                "CONTEXT_NOT_FOUND", "permissionTemplate taskType=" + taskType));

        ConsumerMandate mandate =
                consumerMandates
                        .findByConsumerIdAndPurpose(consumerId, taskType)
                        .orElseThrow(
                                () ->
                                        new PermissionNotIssuableException(
                                                "MANDATE_NOT_FOUND",
                                                "consumerId=" + consumerId + " purpose=" + taskType));

        FinancialCase financialCase =
                new FinancialCase(
                        Identifiers.caseId(now),
                        employeeId,
                        consumerId,
                        taskType,
                        template.getTemplateId(),
                        FinancialCaseStatus.ACTIVE,
                        now,
                        now.plus(template.getDefaultDurationMinutes(), ChronoUnit.MINUTES));

        // 거부되면 여기서 예외가 나고, 트랜잭션이 아무것도 남기지 않는다.
        EffectivePermission permission =
                calculator.calculate(authority, template, financialCase, mandate, now);

        financialCases.save(financialCase);

        TaskPassport passport =
                new TaskPassport(
                        Identifiers.passportId(),
                        agentIdFor(taskType),
                        employeeId,
                        financialCase.getCaseId(),
                        consumerId,
                        taskType,
                        permission.allowedTools(),
                        permission.allowedData(),
                        TaskPassportStatus.ACTIVE,
                        now,
                        financialCase.getExpiresAt(),
                        new SourceVersions(
                                authority.getVersion(),
                                template.getVersion(),
                                financialCase.getVersion(),
                                mandate.getVersion()));
        taskPassports.save(passport);

        String inputRef = Identifiers.inputRef();
        String inputHash = Identifiers.inputHash(inputText);
        AgentRun agentRun =
                new AgentRun(
                        Identifiers.agentRunId(),
                        passport.getAgentId(),
                        employeeId,
                        financialCase.getCaseId(),
                        passport.getPassportId(),
                        List.of(inputRef),
                        AgentRunStatus.RUNNING,
                        now);
        agentRuns.save(agentRun);

        // 원문은 남기지 않는다. 해시만 저장한다 — docs/06 §24.
        // contentLanguage 를 지어내지 않는다. 판별하지 않았으므로 비워 둔다 —
        // 감사 시스템에서 없는 사실을 만들어내는 것은 null 보다 나쁘다.
        securedInputs.save(new SecuredAgentInput(inputRef, agentRun.getAgentRunId(), inputHash, null, now));

        // 같은 입력·같은 모델이면 기존 스냅샷을 재사용한다 — docs/06 §24.2.
        // Prompt Risk는 실행마다 새로 계산하는 값이 아니라 입력 버전에 붙은 스냅샷이다.
        // 새로 만들 때는 NOT_EVALUATED다. false로 적으면 "검사했고 음성"이 되어 감사 기록이
        // 거짓이 된다 — docs/04 §7.
        if (promptRiskSnapshots
                .findByInputHashAndModelVersion(inputHash, PromptRiskModel.CURRENT_VERSION)
                .isEmpty()) {
            promptRiskSnapshots.save(
                    PromptRiskSnapshot.notEvaluated(
                            inputRef, inputHash, PromptRiskModel.CURRENT_VERSION, now));
        }

        return new AgentRunStarted(
                agentRun.getAgentRunId(),
                agentRun.getAgentId(),
                employeeId,
                financialCase.getCaseId(),
                passport.getPassportId(),
                consumerId,
                agentRun.getInputRefs(),
                agentRun.getStatus(),
                agentRun.getStartedAt(),
                permission.allowedTools(),
                permission.allowedData());
    }

    /**
     * P0는 업무 종류당 Agent가 하나다.
     *
     * <p>Runtime의 Verified Agent Identity는 Gateway가 Credential로 검증한 값이며, 여기서 정하는 것은
     * Passport가 어느 Agent에 묶이는지뿐이다. 둘이 어긋나면 Runtime에 {@code agentBinding} 위반이 된다.
     */
    private String agentIdFor(TaskType taskType) {
        return taskType == TaskType.LOAN_REVIEW ? "LOAN-AGENT-01" : "UNKNOWN-AGENT";
    }

    @Transactional
    public void fail(String agentRunId) {
        agentRuns.findById(agentRunId).ifPresent(AgentRun::fail);
    }
}
