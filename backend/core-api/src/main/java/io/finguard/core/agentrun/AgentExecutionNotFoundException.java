package io.finguard.core.agentrun;

/** Public 실행 조회 대상이 없다. 구체적인 식별자는 응답에 노출하지 않는다. */
public class AgentExecutionNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AgentExecutionNotFoundException() {
        super("Agent execution was not found");
    }
}
