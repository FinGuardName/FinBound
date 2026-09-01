package io.finguard.core.audit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.finguard.core.domain.AuditEvent;
import io.finguard.core.domain.ResolvedAuditContext;
import io.finguard.core.repository.AuditEventRepository;

/**
 * Resolver가 계산한 근거를 선저장된 감사행에 적는다. {@code docs/04-api-contract.md} §11 · §14.
 *
 * <p><strong>왜 별도 빈인가.</strong> Context Resolver 안에 두고 {@code this}로 부르면 Spring 프록시를
 * 거치지 않아 트랜잭션 어노테이션이 무시된다. 호출 경로가 눈에 보이게 빈을 나눈다.
 *
 * <p><strong>왜 실패하면 막는가.</strong> {@code docs/02-architecture.md}:143-149의 순서상 감사행이
 * 먼저 생기고 resolve가 뒤에 온다. 행이 없다는 것은 Gateway가 그 순서를 어겼다는 뜻이다. 여기서
 * 그냥 지나가면 Core는 인가 근거를 반환하는데 <strong>그 근거를 받을 행이 없고</strong>, 남는 흔적은
 * 만료되는 운영 로그뿐이다. 감사 기록이 증거이려면 근거 없이 판단이 나가는 경로가 있으면 안 된다.
 *
 * <p>거부에 실을 Reason Code는 아직 없다. {@code docs/06-common-conventions.md} §20의 목록은 Runtime
 * 집행의 거부 사유를 정의하며 "호출 순서 위반"에 해당하는 값이 없다. 억지로 비슷한 값을 넣지 않고
 * 별도 계약 결정으로 분리한다 — PR #56 리뷰에서 합의한 범위다.
 */
@Service
public class AuditEvidenceRecorder {

    private final AuditEventRepository auditEvents;

    public AuditEvidenceRecorder(AuditEventRepository auditEvents) {
        this.auditEvents = auditEvents;
    }

    /**
     * 부르는 쪽 트랜잭션에 합류한다. 별도 트랜잭션으로 떼면 기록이 실패해도 resolve가 성공하는데,
     * 그게 바로 막으려는 상태다.
     */
    @Transactional
    public void record(String requestId, ResolvedAuditContext context) {
        AuditEvent event =
                auditEvents
                        .findByRequestId(requestId)
                        .orElseThrow(() -> new AuditEvidenceRejectedException(requestId));

        try {
            event.recordResolvedContext(context);
        } catch (IllegalStateException rejected) {
            // 이미 확정된 기록이거나, 같은 requestId에 다른 근거가 온 경우다. 둘 다 덮어쓰면 안 된다.
            throw new AuditEvidenceRejectedException(requestId, rejected);
        }
    }
}
