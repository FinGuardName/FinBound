package io.finguard.core.agentrun;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AgentRunApiExceptionHandler {
    @ExceptionHandler(AgentSimulatorCallException.class)
    ResponseEntity<ProblemDetail> handleAgentSimulator(AgentSimulatorCallException exception) {
        HttpStatus status = "AGENT_SIMULATOR_TIMEOUT".equals(exception.getErrorCode())
                ? HttpStatus.GATEWAY_TIMEOUT
                : HttpStatus.BAD_GATEWAY;
        ProblemDetail body = ProblemDetail.forStatus(status);
        body.setDetail("Agent 실행을 시작할 수 없습니다.");
        body.setProperty("reasonCode", exception.getErrorCode());
        return ResponseEntity.status(status).body(body);
    }
}
