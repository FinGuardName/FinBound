package io.finguard.core.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import io.finguard.core.context.ContextLookupException;
import io.finguard.core.permission.PermissionNotIssuableException;

/**
 * Core API의 오류 응답. 본문은 {@code application/problem+json}이고 {@code reasonCode}를 싣는다.
 *
 * <p><strong>거부된 값을 응답에 담지 않는다.</strong> 필드 이름과 사유만 남긴다 —
 * {@code inputText}는 비신뢰 입력이라 검증 메시지에 값이 섞여 나가면 원문이 그대로 새어 나간다
 * ({@code docs/06-common-conventions.md} §24 · §26).
 */
@RestControllerAdvice
public class CoreApiExceptionHandler {

    @ExceptionHandler(ContextLookupException.class)
    ResponseEntity<ProblemDetail> handleContextLookup(ContextLookupException exception) {
        return problem(
                HttpStatus.NOT_FOUND,
                exception.getReasonCode(),
                "요청한 Runtime Context를 찾을 수 없습니다.");
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

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String reasonCode, String detail) {
        ProblemDetail body = ProblemDetail.forStatus(status);
        body.setDetail(detail);
        body.setProperty("reasonCode", reasonCode);
        return ResponseEntity.status(status).body(body);
    }
}
