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
     *
     * <p><strong>행을 찾은 뒤 그 행이 이 실행의 것인지 확인한다.</strong> {@code requestId}는 요청
     * 본문에서 오므로 대상을 고르는 값이지 소유를 증명하는 값이 아니다({@code docs/04} §1.4가
     * {@code verifiedAgentId}에 대해 이미 같은 입장이다). 확인하지 않으면 남의 감사행에 내 Passport와
     * Scope를 적을 수 있고, set-once라 원래 주인은 자기 근거를 영영 남기지 못한다.
     *
     * <p><strong>대조 대상은 {@code agentRunId}이지 Agent가 아니다.</strong> 헤더의 Agent가 감사행과
     * 다른 것은 거부할 일이 아니라 {@code agentBinding} VIOLATION으로 <em>기록할</em> 일이다. 그걸
     * 409로 막으면 스푸핑 시도가 아무 흔적도 남기지 않고 사라진다. 실행 단위가 같은지만 본다.
     *
     * <p>불일치를 부재와 같은 409로 돌려준다. 나누면 어떤 requestId가 살아 있는지 되물어 확인하는
     * 통로가 된다({@code docs/06} §26).
     */
    @Transactional
    public void record(String requestId, String agentRunId, ResolvedAuditContext context) {
        AuditEvent event =
                auditEvents
                        .findByRequestId(requestId)
                        .filter(candidate -> candidate.getAgentRunId().equals(agentRunId))
                        .orElseThrow(() -> new AuditEvidenceRejectedException(requestId));

        try {
            event.recordResolvedContext(context);
        } catch (IllegalStateException rejected) {
            // 이미 확정된 기록이거나, 같은 requestId에 다른 근거가 온 경우다. 둘 다 덮어쓰면 안 된다.
            throw new AuditEvidenceRejectedException(requestId, rejected);
        }
    }
}
