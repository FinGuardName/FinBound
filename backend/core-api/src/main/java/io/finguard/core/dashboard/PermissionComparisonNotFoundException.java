package io.finguard.core.dashboard;

/**
 * 비교에 필요한 AgentRun·Passport·Authority 중 하나가 없다.
 *
 * <p>셋을 구분하지 않는다. 어느 것이 없는지 밝히면 존재 여부를 캐물어 식별자를 훑는 통로가 된다.
 */
public class PermissionComparisonNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PermissionComparisonNotFoundException(String agentRunId) {
        super("No permission comparison for agent run " + agentRunId);
    }
}
