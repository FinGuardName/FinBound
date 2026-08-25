package io.finguard.core.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import io.finguard.core.history.BehaviorHistoryController;

/**
 * 계약에 있는 엔드포인트가 실제로 라우팅되는지 고정한다.
 *
 * <p>아직 구현되지 않은 엔드포인트는 고정 응답이 아니라 501을 낸다. 붙이는 쪽이 404(경로를 잘못 붙임)와
 * 501(아직 안 만듦)을 구분할 수 있어야 하고, 가짜 성공 응답 때문에 통합이 끝났다고 착각하는 일이 없어야 한다.
 */
class InternalApiSurfaceTest {

    private final MockMvc mockMvc =
            MockMvcBuilders.standaloneSetup(new BehaviorHistoryController())
                    .build();

    // POST /api/v1/agent-runs 는 이슈 #19에서 구현됐다. 동작 검증은 AgentRunApiTest 가 한다.
    // POST /internal/v1/context/resolve 는 이슈 #20에서 구현됐다. 동작 검증은 ContextResolveApiTest 가 한다.

    // Audit / Outcome / SecurityAuthEvent API는 이슈 #21에서 구현됐다.
    // 실제 인증 필터·PostgreSQL을 포함한 검증은 AuditPersistenceApiTest가 한다.

    @Test
    void behaviorHistoryIsRoutedAndNotImplementedYet() throws Exception {
        mockMvc.perform(get("/internal/v1/agents/LOAN-AGENT-01/behavior-history").param("window", "5m"))
                .andExpect(status().isNotImplemented());
    }

    @Test
    void unknownInternalPathIsNotRouted() throws Exception {
        // 501과 404가 구분되는지 — 이게 아니면 위 테스트들이 무엇도 증명하지 못한다.
        mockMvc.perform(post("/internal/v1/does-not-exist")).andExpect(status().isNotFound());
    }
}
