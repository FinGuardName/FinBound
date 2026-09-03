package io.finguard.core.dashboard;

import java.time.Instant;
import java.util.Set;

import io.finguard.core.domain.DataType;
import io.finguard.core.domain.TaskPassportStatus;
import io.finguard.core.domain.Tool;

/**
 * Employee Authority와 이번 업무의 Agent Effective Permission을 나란히 놓은 결과. docs/01 F20.
 *
 * <p>{@code withheldTools}·{@code withheldData}는 두 집합의 차집합이다. 화면이 실제로 답해야 하는
 * 질문이 "무엇이 넘어가지 않았는가"라서 클라이언트가 매번 빼기를 하도록 두지 않고 서버가 계산한다 —
 * 빼는 방향을 뒤집으면 화면이 정반대를 말한다.
 *
 * <p>{@code AGENTS.md}의 불변식대로 Effective ⊆ Authority가 성립하면 이 차집합이 곧 Agent가 갖지
 * 못한 권한이다. 성립하지 않으면 {@code escalatedTools}가 비어 있지 않게 되고, 그건 권한 상승이다.
 */
public record PermissionComparisonResponse(
        String agentRunId,
        String agentId,
        String employeeId,
        String caseId,
        String passportId,
        TaskPassportStatus passportStatus,
        Instant passportExpiresAt,
        Permissions employeeAuthority,
        Permissions agentEffectivePermission,
        Set<Tool> withheldTools,
        Set<DataType> withheldData,
        Set<Tool> escalatedTools,
        Set<DataType> escalatedData) {

    public record Permissions(Set<Tool> allowedTools, Set<DataType> allowedData) {
    }
}
