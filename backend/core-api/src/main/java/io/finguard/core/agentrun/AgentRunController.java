package io.finguard.core.agentrun;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import io.finguard.core.domain.ReasonCode;
import io.finguard.core.risk.RequestTrace;
import io.finguard.core.security.CoreApiAccessDeniedException;
import io.finguard.core.security.CoreApiPrincipal;
import io.finguard.core.security.CoreApiRole;
import io.finguard.core.security.RequiresRole;
import jakarta.validation.Valid;

/**
 * AgentRun 생성과 Task Passport 발급 — {@code docs/04-api-contract.md} §3.
 *
 * <p>Core가 AgentRun과 TaskPassport를 소유한다({@code docs/02-architecture.md} §7.1).
 * Agent 측은 이 엔드포인트를 호출한다.
 */
@RestController
public class AgentRunController {

    private final AgentRunService agentRunService;
    private final AgentRunPreparer preparer;
    private final AgentExecutionService executionService;

    public AgentRunController(
            AgentRunService agentRunService,
            AgentRunPreparer preparer,
            AgentExecutionService executionService) {
        this.agentRunService = agentRunService;
        this.preparer = preparer;
        this.executionService = executionService;
    }

    /**
     * Body의 {@code employeeId}는 조회할 업무 대상을 표시하는 값이지 인증수단이 아니다 — §3.
     *
     * <p>대조를 {@link AgentRunService#start} 진입 전에 끝낸다. 그 안으로 들어가면 이 값으로 해당 직원의
     * 권한을 조회해 Task Passport가 발급되는데, 발급된 Passport는 {@code agentId}·{@code caseId}·
     * {@code sourceVersions}가 내부적으로 일관돼서 <strong>Runtime Resolver가 나중에 되돌릴 수 없다.</strong>
     * 그 시점에는 대조할 인증 Identity가 남아 있지 않기 때문이다.
     */
    @PostMapping("/api/v1/agent-runs")
    @RequiresRole(CoreApiRole.OPERATOR)
    public ResponseEntity<AgentRunResponse> create(
            @Valid @RequestBody AgentRunCreateRequest request,
            CoreApiPrincipal principal,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestHeader(value = "Traceparent", required = false) String traceparent) {
        if (!principal.employeeId().equals(request.employeeId())) {
            // 어느 쪽 값도 메시지에 담지 않는다 — docs/06 §26.
            throw new CoreApiAccessDeniedException(
                    ReasonCode.EMPLOYEE_IDENTITY_MISMATCH,
                    "요청한 Employee가 Credential에 묶인 Employee와 다릅니다.");
        }

        // 식별자 생성과 Prompt Risk 평가는 쓰기 트랜잭션 밖이다. 느린 HTTP가 DB 커넥션을
        // 붙잡으면 안 된다 — AgentRunPreparer 참조. 여기서 순서를 정하는 이유는 start가
        // @Transactional 이라 자기 호출로는 프록시를 거치지 않기 때문이다.
        //
        // 추적 헤더는 들어온 값을 그대로 이어 붙인다 — docs/04 §2. 없으면 X-Request-Id만 만들고
        // Traceparent는 지어내지 않는다.
        PreparedAgentRun prepared =
                preparer.prepare(request.inputText(), RequestTrace.of(requestId, traceparent));

        AgentRunStarted started =
                agentRunService.start(
                        request.employeeId(),
                        request.consumerId(),
                        request.taskType(),
                        prepared,
                        request.scenario());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        new AgentRunResponse(
                                started.agentRunId(),
                                started.agentId(),
                                started.employeeId(),
                                started.caseId(),
                                started.passportId(),
                                started.inputRefs(),
                                started.status(),
                                started.startedAt()));
    }

    /** PR #65와 Issue #74에서 합의한 browser-facing 실행 상태 조회 계약. */
    @GetMapping("/api/v1/agent-runs/{agentRunId}/execution")
    @RequiresRole({CoreApiRole.VIEWER, CoreApiRole.OPERATOR})
    public ResponseEntity<AgentExecutionResponse> execution(
            @PathVariable String agentRunId, CoreApiPrincipal principal) {
        return ResponseEntity.ok(executionService.find(agentRunId, principal));
    }
}
