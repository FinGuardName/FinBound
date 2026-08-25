package io.finguard.agent.agentrun.api;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebInputException;

import io.finguard.agent.agentrun.service.AgentRunCreationException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = AgentRunController.class)
public class AgentRunExceptionHandler {
    @ExceptionHandler({WebExchangeBindException.class, ServerWebInputException.class})
    public ResponseEntity<AgentRunApiError> handleInvalidRequest() {
        return ResponseEntity.badRequest().body(new AgentRunApiError(
                "INVALID_AGENT_RUN_REQUEST",
                "The AgentRun request is invalid"
        ));
    }

    @ExceptionHandler(AgentRunCreationException.class)
    public ResponseEntity<AgentRunApiError> handleCreationFailure() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new AgentRunApiError(
                "CONTEXT_SERVICE_UNAVAILABLE",
                "AgentRun context could not be prepared"
        ));
    }
}
