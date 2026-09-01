package io.finguard.core.agentrun;

import io.finguard.core.domain.AgentSimulationScenario;

/**
 * Agent Simulator 실행 요청. {@code docs/04-api-contract.md} §3.1.
 *
 * <p>Core가 밖으로 나가는 <strong>첫 호출</strong>이다. 그래서 인터페이스로 끊어 둔다 — 전송 방식이
 * 바뀌어도({@code RestClient} → 메시지 큐 등) 오케스트레이션은 그대로 둘 수 있다.
 */
public interface AgentSimulationClient {

    /**
     * Agent에게 실행을 지시하고 끝날 때까지 기다린다.
     *
     * <p>Agent는 동기 응답만 준다. 호출자(Frontend)에게 비동기인 것은 이 메서드를 부르는 쪽이
     * 별도 스레드에 있기 때문이지 여기가 비동기라서가 아니다.
     *
     * @throws AgentSimulationFailedException 부르지 못했거나 Agent가 실패를 알린 경우
     */
    void simulate(String agentRunId, String passportId, AgentSimulationScenario scenario);
}
