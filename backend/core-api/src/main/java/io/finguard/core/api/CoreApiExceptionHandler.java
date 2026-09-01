package io.finguard.core.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import io.finguard.core.audit.AuditOperationException;
import io.finguard.core.context.ContextLookupException;
import io.finguard.core.dashboard.AuditEventNotFoundException;
import io.finguard.core.dashboard.PermissionComparisonNotFoundException;
import io.finguard.core.history.InvalidBehaviorHistoryWindowException;
import io.finguard.core.permission.PermissionNotIssuableException;
import io.finguard.core.security.CoreApiAccessDeniedException;
import io.finguard.core.security.CoreApiCredentialFilter;
import io.finguard.core.securityevent.SecurityEventWriteException;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Core API의 오류 응답. 본문은 {@code application/problem+json}이고 {@code reasonCode}를 싣는다.
 *
 * <p><strong>거부된 값을 응답에 담지 않는다.</strong> 필드 이름과 사유만 남긴다 —
 * {@code inputText}는 비신뢰 입력이라 검증 메시지에 값이 섞여 나가면 원문이 그대로 새어 나간다
 * ({@code docs/06-common-conventions.md} §24 · §26).
 */
@RestControllerAdvice
public class CoreApiExceptionHandler {

    @ExceptionHandler(AuditOperationException.class)
    ResponseEntity<ProblemDetail> handleAuditOperation(AuditOperationException exception) {
        HttpStatus status = switch (exception.getKind()) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case DUPLICATE -> HttpStatus.CONFLICT;
            case INVALID_OUTCOME -> HttpStatus.BAD_REQUEST;
            case WRITE_FAILED -> HttpStatus.SERVICE_UNAVAILABLE;
        };
        return problem(status, exception.getReasonCode(), "Business Audit 요청을 처리할 수 없습니다.");
    }

    @ExceptionHandler(SecurityEventWriteException.class)
    ResponseEntity<ProblemDetail> handleSecurityEventWrite(SecurityEventWriteException exception) {
        return problem(
                HttpStatus.SERVICE_UNAVAILABLE,
                "SECURITY_EVENT_WRITE_FAILED",
                "Security Event를 저장할 수 없습니다.");
    }

    @ExceptionHandler(ContextLookupException.class)
    ResponseEntity<ProblemDetail> handleContextLookup(ContextLookupException exception) {
        return problem(
                HttpStatus.NOT_FOUND,
                exception.getReasonCode(),
                "요청한 Runtime Context를 찾을 수 없습니다.");
    }

    @ExceptionHandler(InvalidBehaviorHistoryWindowException.class)
    ResponseEntity<ProblemDetail> handleInvalidHistoryWindow(
            InvalidBehaviorHistoryWindowException exception) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_TOOL_REQUEST", "조회 시간 범위가 올바르지 않습니다.");
    }

    /**
     * 요청 형식은 멀쩡한데 현재 권한 상태로는 만들 수 없는 경우.
     *
     * <p>404가 아닌 이유는 이 URL의 자원이 없어서가 아니기 때문이고, 403이 아닌 이유는 호출자의 신원
     * 문제가 아니기 때문이다. 구체적인 사유는 {@code reasonCode}가 담는다({@code docs/06} §20).
     */
    @ExceptionHandler(PermissionNotIssuableException.class)
    ResponseEntity<ProblemDetail> handleNotIssuable(PermissionNotIssuableException exception) {
        return problem(
                HttpStatus.UNPROCESSABLE_ENTITY,
                exception.getReasonCode(),
                "Task Passport를 발급할 수 없는 상태입니다.");
    }

    /**
     * 인증은 됐지만 자격이 없다. 어느 Role이 필요한지, 어떤 Employee가 묶여 있는지는 알려주지 않는다 —
     * 거부 사유를 좁혀 주면 그것 자체가 탐색의 실마리가 된다({@code docs/06} §26).
     */
    @ExceptionHandler(CoreApiAccessDeniedException.class)
    ResponseEntity<ProblemDetail> handleAccessDenied(
            CoreApiAccessDeniedException exception, HttpServletRequest request) {
        // 사유를 필터가 집어갈 수 있게 남긴다. 기록은 거기 한 곳에서 한다 — CoreApiCredentialFilter.
        request.setAttribute(
                CoreApiCredentialFilter.DENIED_REASON_ATTRIBUTE, exception.getReasonCode());
        return problem(
                HttpStatus.FORBIDDEN, exception.getReasonCode().name(), "이 요청을 수행할 자격이 없습니다.");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException exception) {
        // 필드 이름만 모은다. getRejectedValue()는 절대 담지 않는다.
        List<String> invalidFields =
                exception.getBindingResult().getFieldErrors().stream().map(error -> error.getField()).toList();

        ResponseEntity<ProblemDetail> response =
                problem(HttpStatus.BAD_REQUEST, "INVALID_TOOL_REQUEST", "요청 형식이 올바르지 않습니다.");
        response.getBody().setProperty("invalidFields", invalidFields);
        return response;
    }

    /** 본문 자체를 읽지 못한 경우(잘못된 JSON, 알 수 없는 Enum 값 등). 예외 메시지에 원문 조각이 섞이므로 옮기지 않는다. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> handleUnreadableBody(HttpMessageNotReadableException exception) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_TOOL_REQUEST", "요청 본문을 해석할 수 없습니다.");
    }

    /**
     * Dashboard 조회 대상이 없다. {@code reasonCode}를 붙이지 않는다 — {@code docs/06} §20의 Reason Code는
     * Runtime 집행의 거부 사유를 정의하며 읽기 조회 실패에 해당하는 값이 없다. 억지로 가장 비슷한 값을
     * 넣으면 대시보드가 집행 사유를 표시하게 되고, 그건 사실이 아니다.
     */
    @ExceptionHandler({AuditEventNotFoundException.class, PermissionComparisonNotFoundException.class})
    ResponseEntity<ProblemDetail> handleDashboardLookup(RuntimeException exception) {
        ProblemDetail body = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        body.setDetail("요청한 기록을 찾을 수 없습니다.");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String reasonCode, String detail) {
        ProblemDetail body = ProblemDetail.forStatus(status);
        body.setDetail(detail);
        body.setProperty("reasonCode", reasonCode);
        return ResponseEntity.status(status).body(body);
    }
}
