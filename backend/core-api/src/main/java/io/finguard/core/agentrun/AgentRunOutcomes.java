package io.finguard.core.agentrun;

/**
 * 실행 결과를 AgentRun에 반영한다.
 *
 * <p>인터페이스로 끊어 둔 이유는 트랜잭션 경계 때문이다. Agent 호출은 <strong>트랜잭션 밖</strong>에서
 * 일어나야 하고(느린 HTTP가 DB 커넥션을 붙잡으면 안 된다), 상태 기록만 짧은 트랜잭션에서 한다.
 * 두 일을 한 클래스에 두면 {@code this} 호출이 되어 프록시를 안 거친다.
 */
public interface AgentRunOutcomes {

    void complete(String agentRunId);

    void fail(String agentRunId);
}
