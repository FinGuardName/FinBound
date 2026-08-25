package io.finguard.agent.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebInputException;

import io.finguard.agent.gateway.GatewayCallException;

@RestControllerAdvice
public class AgentApiExceptionHandler {
    @ExceptionHandler({WebExchangeBindException.class, ServerWebInputException.class})
    public ResponseEntity<AgentApiError> handleInvalidRequest() {
        return ResponseEntity.badRequest().body(new AgentApiError(
                "INVALID_AGENT_SIMULATION_REQUEST",
                "The agent simulation request is invalid"
        ));
    }

    @ExceptionHandler(GatewayCallException.class)
    public ResponseEntity<AgentApiError> handleGatewayCall(GatewayCallException exception) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new AgentApiError(
                exception.errorCode(),
                "The Gateway call could not be completed"
        ));
    }
}
