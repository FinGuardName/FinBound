package io.finguard.core.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Enum 값은 그대로 JSON 계약에 실린다.
 *
 * <p>{@code docs/04-api-contract.md}가 {@code "tool": "CREDIT_SCORE_READ"} 처럼 이 이름들을 그대로
 * 주고받으므로, 상수 이름을 바꾸거나 값을 더하면 계약이 조용히 달라진다. 여기서 집합을 고정한다.
 * 값을 바꿔야 한다면 {@code docs/06-common-conventions.md}를 먼저 고치고 이 테스트를 함께 고친다.
 */
class DomainEnumContractTest {

    @Test
    void toolMatchesConvention() {
        // docs/06 §16
        assertThat(Tool.values())
                .extracting(Enum::name)
                .containsExactly("CREDIT_SCORE_READ", "INCOME_READ", "DEBT_READ");
    }

    @Test
    void dataTypeMatchesConvention() {
        // docs/06 §17
        assertThat(DataType.values())
                .extracting(Enum::name)
                .containsExactly("CREDIT_SCORE", "INCOME", "DEBT");
    }

    @Test
    void taskTypeMatchesConvention() {
        // docs/06 §18
        assertThat(TaskType.values()).extracting(Enum::name).containsExactly("LOAN_REVIEW");
    }

    @Test
    void statusEnumsMatchConvention() {
        assertThat(EmployeeAuthorityStatus.values())
                .extracting(Enum::name)
                .containsExactly("ACTIVE", "INACTIVE"); // §4
        assertThat(ConsumerMandateStatus.values())
                .extracting(Enum::name)
                .containsExactly("ACTIVE", "REVOKED", "EXPIRED"); // §5
        assertThat(PermissionTemplateStatus.values())
                .extracting(Enum::name)
                .containsExactly("ACTIVE", "INACTIVE"); // §6
        assertThat(FinancialCaseStatus.values())
                .extracting(Enum::name)
                .containsExactly("ACTIVE", "COMPLETED", "EXPIRED", "CANCELLED"); // §7
        assertThat(TaskPassportStatus.values())
                .extracting(Enum::name)
                .containsExactly("ACTIVE", "EXPIRED", "REVOKED", "STALE"); // §8
        assertThat(AgentRunStatus.values())
                .extracting(Enum::name)
                .containsExactly("CREATED", "RUNNING", "COMPLETED", "FAILED"); // §9
        assertThat(AuditStatus.values())
                .extracting(Enum::name)
                .containsExactly("PROCESSING", "COMPLETED", "ERROR"); // §10
    }

    @Test
    void policyDecisionHasNoErrorValue() {
        // docs/06 §11 — 시스템 장애는 Decision Enum이 아니라 Audit 상태로 표현한다.
        assertThat(PolicyDecision.values()).extracting(Enum::name).containsExactly("ALLOW", "BLOCK");
    }

    @Test
    void promptRiskDistinguishesNotEvaluatedFromNegative() {
        // docs/04 §7 — "검사하지 않았음"과 "검사했고 음성"을 섞으면 Audit 기록이 거짓이 된다.
        assertThat(PromptRiskEvaluationStatus.values())
                .extracting(Enum::name)
                .containsExactly("EVALUATED", "NOT_EVALUATED");
    }

    @Test
    void promptRiskLevelMatchesConvention() {
        assertThat(PromptRiskLevel.values())
                .extracting(Enum::name)
                .containsExactly("LOW", "ALERT", "CRITICAL");
    }

    @Test
    void agentSimulationScenarioMatchesTheAgentModuleVocabulary() {
        // docs/04 §3.1이 값 일곱 개를 정의하지만 한 자리에 모아 두지 않았다 — 표에는 새 다섯 개만
        // 있고 나머지 둘은 앞 문단에 있다. 그래서 순서의 근거는 Agent 모듈의 같은 이름 enum이다.
        // 타입을 공유하지 않으므로 어휘가 갈라지면 Core가 보낸 값을 Agent가 역직렬화하지 못한다.
        // 컴파일러가 못 잡는 종류라 여기서 고정한다.
        assertThat(AgentSimulationScenario.values())
                .extracting(Enum::name)
                .containsExactly(
                        "NORMAL_CREDIT_SCORE",
                        "NORMAL_INCOME",
                        "NORMAL_DEBT",
                        "CASE_SCOPE_ATTACK",
                        "TOOL_SCOPE_ATTACK",
                        "DATA_SCOPE_ATTACK",
                        "MANDATE_SCOPE_ATTACK");
    }
}
