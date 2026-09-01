package io.finguard.core.audit;

/**
 * 근거를 적을 감사행이 없거나, 이미 다른 근거가 적혀 있다.
 *
 * <p>호출 순서 위반이므로 요청 형식 오류(400)도 대상 부재(404)도 아니다 — 서버의 현재 상태와
 * 충돌하는 요청이라 409다.
 *
 * <p>받은 requestId를 응답에 담지 않는다({@code docs/06} §26). 어떤 requestId가 감사행을 가지고
 * 있는지를 되물어 확인하는 통로가 된다.
 */
public class AuditEvidenceRejectedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AuditEvidenceRejectedException(String requestId) {
        super("No audit event is waiting for evidence: " + requestId);
    }

    public AuditEvidenceRejectedException(String requestId, Throwable cause) {
        super("Audit event cannot take this evidence: " + requestId, cause);
    }
}
