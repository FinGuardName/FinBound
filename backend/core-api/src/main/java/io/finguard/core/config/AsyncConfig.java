package io.finguard.core.config;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Core에서 요청 응답 뒤에 이어지는 작업을 돌리는 실행기.
 *
 * <p>Core의 다른 모든 작업은 동기다 — 누군가 그 응답을 기다리거나, 실패하면 막아야 하기 때문이다.
 * 비동기가 필요한 곳은 지금 하나뿐이다: AgentRun 생성 응답을 돌려준 뒤 Agent를 깨우는 일.
 * 이걸 동기로 하면 Agent가 Gateway를 거쳐 Core로 되돌아오는 동안 원래 요청이 열린 채 대기한다.
 *
 * <p><strong>큐를 무한으로 두지 않는다.</strong> 무한 큐는 부하를 메모리로 옮길 뿐이고, 그러면
 * 실행 지시가 몇 분 뒤에 나가면서 Passport가 이미 만료돼 있을 수 있다. 넘치면 즉시 거절한다.
 *
 * <p>거절을 <strong>부르는 쪽이 잡아야 한다.</strong> {@link RejectedExecutionException}을 던지는 것만으로는
 * 아무도 알지 못한다 — 이 실행기는 커밋 이후에 쓰이므로 호출자는 이미 응답을 받았고, 트랜잭션 완료
 * 단계에서 나온 예외는 Spring이 로그만 남기고 삼킨다. 그래서 {@code AgentRunLauncher}가 직접 제출하고
 * 거절을 잡아 실행을 {@code FAILED}로 남긴다.
 *
 * <p>같은 이유로 {@code CallerRuns} 정책을 쓰지 않는다. 그건 커밋 직후 스레드에서 긴 HTTP 호출을
 * 돌리게 되고, 그 스레드는 요청 스레드일 수 있다.
 */
@Configuration
public class AsyncConfig {

    @Bean
    Executor agentLaunchExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("finguard-core-");
        // 기본 정책은 호출 스레드에서 실행(CallerRuns)인데, 여기서는 그게 응답을 붙잡는다.
        executor.setRejectedExecutionHandler((task, pool) -> {
            throw new RejectedExecutionException("Core task executor is saturated");
        });
        executor.initialize();
        return executor;
    }
}
