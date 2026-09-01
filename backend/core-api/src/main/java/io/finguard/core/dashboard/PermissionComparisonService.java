package io.finguard.core.dashboard;

import java.util.EnumSet;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.finguard.core.domain.AgentRun;
import io.finguard.core.domain.DataType;
import io.finguard.core.domain.EmployeeAuthority;
import io.finguard.core.domain.TaskPassport;
import io.finguard.core.domain.Tool;
import io.finguard.core.repository.AgentRunRepository;
import io.finguard.core.repository.EmployeeAuthorityRepository;
import io.finguard.core.repository.TaskPassportRepository;

/**
 * LoanAgent 실행 화면의 "현재 업무 보호" 패널이 읽는 비교. docs/01 F20 · docs/04 §15.
 *
 * <p>발급 시점의 계산을 다시 하지 않는다. Task Passport에 이미 박혀 있는 값을 읽는다 — 지금 다시
 * 계산하면 그 사이 원본이 바뀐 경우 화면이 "그때 무엇을 허용했는지"가 아니라 "지금이면 무엇을
 * 허용할지"를 보여주게 된다. 이 화면이 답해야 하는 건 전자다.
 */
@Service
@Transactional(readOnly = true)
public class PermissionComparisonService {

    private final AgentRunRepository agentRuns;
    private final TaskPassportRepository passports;
    private final EmployeeAuthorityRepository authorities;

    public PermissionComparisonService(
            AgentRunRepository agentRuns,
            TaskPassportRepository passports,
            EmployeeAuthorityRepository authorities) {
        this.agentRuns = agentRuns;
        this.passports = passports;
        this.authorities = authorities;
    }

    public PermissionComparisonResponse compare(String agentRunId) {
        AgentRun run =
                agentRuns
                        .findById(agentRunId)
                        .orElseThrow(() -> new PermissionComparisonNotFoundException(agentRunId));
        TaskPassport passport =
                passports
                        .findById(run.getPassportId())
                        .orElseThrow(() -> new PermissionComparisonNotFoundException(agentRunId));
        EmployeeAuthority authority =
                authorities
                        .findById(run.getEmployeeId())
                        .orElseThrow(() -> new PermissionComparisonNotFoundException(agentRunId));

        Set<Tool> authorityTools = copyOf(Tool.class, authority.getAllowedTools());
        Set<DataType> authorityData = copyOf(DataType.class, authority.getAllowedData());
        Set<Tool> passportTools = copyOf(Tool.class, passport.getAllowedTools());
        Set<DataType> passportData = copyOf(DataType.class, passport.getAllowedData());

        return new PermissionComparisonResponse(
                run.getAgentRunId(),
                run.getAgentId(),
                run.getEmployeeId(),
                run.getCaseId(),
                passport.getPassportId(),
                passport.getStatus(),
                passport.getExpiresAt(),
                new PermissionComparisonResponse.Permissions(authorityTools, authorityData),
                new PermissionComparisonResponse.Permissions(passportTools, passportData),
                difference(Tool.class, authorityTools, passportTools),
                difference(DataType.class, authorityData, passportData),
                difference(Tool.class, passportTools, authorityTools),
                difference(DataType.class, passportData, authorityData));
    }

    /** lazy 컬렉션을 트랜잭션 안에서 복사한다. 그대로 내보내면 직렬화 시점에 세션이 없다. */
    private static <E extends Enum<E>> Set<E> copyOf(Class<E> type, Set<E> values) {
        Set<E> copy = EnumSet.noneOf(type);
        copy.addAll(values);
        return copy;
    }

    private static <E extends Enum<E>> Set<E> difference(Class<E> type, Set<E> left, Set<E> right) {
        Set<E> result = EnumSet.noneOf(type);
        result.addAll(left);
        result.removeAll(right);
        return result;
    }
}
