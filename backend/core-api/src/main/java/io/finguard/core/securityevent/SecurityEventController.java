package io.finguard.core.securityevent;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/**
 * 인증 실패를 Business Audit과 분리해 최소 기록하는 API.
 */
@RestController
public class SecurityEventController {

    private final SecurityEventService service;

    public SecurityEventController(SecurityEventService service) {
        this.service = service;
    }

    @PostMapping("/internal/v1/security-events/auth-failure")
    public ResponseEntity<SecurityEventResponse> recordAuthFailure(
            @Valid @RequestBody AuthFailureEventRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.recordAuthFailure(request));
    }
}
