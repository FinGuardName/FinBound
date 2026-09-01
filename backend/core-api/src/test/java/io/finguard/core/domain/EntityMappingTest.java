package io.finguard.core.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import jakarta.persistence.EntityManager;

/**
 * 엔티티 매핑이 실제 PostgreSQL에서 지켜지는지 확인한다.
 *
 * <p>스키마는 {@code V1__baseline.sql}이 만들고 Hibernate는 {@code validate}만 한다. 그래서
 * "컬럼이 있다"까지는 기동 자체가 보증한다. 여기서 보는 것은 그 다음이다 — 값이 왕복하는가,
 * 제약이 실제로 거부하는가, 시각이 보존되는가.
 */
@SpringBootTest(
        properties = {
            "finguard.internal.credential=test-internal-credential",
            "finguard.api.viewer-credential=test-viewer-credential",
            "finguard.api.operator-credential=test-operator-credential",
            "finguard.api.operator-employee-id=EMP-101",
        })
@Testcontainers
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class EntityMappingTest {

    private static final Instant ISSUED = Instant.parse("2026-08-17T05:00:00Z");
    private static final Instant EXPIRES = Instant.parse("2030-12-31T14:59:59Z");

    @Container
    @ServiceConnection
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.10-alpine");

    @Autowired
    private EntityManager em;

    @BeforeEach
    void insertReferencedRows() {
        em.persist(new Employee("EMP-900", ISSUED));
        em.persist(new Consumer("CUST-900", ISSUED));
        em.persist(
                new PermissionTemplate(
                        "TPL-900",
                        TaskType.LOAN_REVIEW,
                        EnumSet.allOf(Tool.class),
                        EnumSet.allOf(DataType.class),
                        60,
                        PermissionTemplateStatus.ACTIVE));
        em.flush();
    }

    @Test
    void employeeAuthorityRoundTripsCollectionsAndEnums() {
        em.persist(
                new EmployeeAuthority(
                        "EMP-900",
                        EmployeeAuthorityStatus.ACTIVE,
                        CustomerScope.ALL,
                        EnumSet.of(Tool.CREDIT_SCORE_READ, Tool.INCOME_READ),
                        EnumSet.of(DataType.CREDIT_SCORE)));
        em.flush();
        em.clear();

        EmployeeAuthority found = em.find(EmployeeAuthority.class, "EMP-900");

        assertThat(found.getStatus()).isEqualTo(EmployeeAuthorityStatus.ACTIVE);
        assertThat(found.getAllowedCustomerScope()).isEqualTo(CustomerScope.ALL);
        assertThat(found.getAllowedTools())
                .containsExactlyInAnyOrder(Tool.CREDIT_SCORE_READ, Tool.INCOME_READ);
        assertThat(found.getAllowedData()).containsExactly(DataType.CREDIT_SCORE);
        assertThat(found.isActive()).isTrue();
    }

    @Test
    void employeeAuthorityGetterCollectionsAreNotModifiable() {
        EmployeeAuthority authority =
                new EmployeeAuthority(
                        "EMP-900",
                        EmployeeAuthorityStatus.INACTIVE,
                        CustomerScope.ALL,
                        EnumSet.of(Tool.DEBT_READ),
                        EnumSet.of(DataType.DEBT));

        assertThat(authority.isActive()).isFalse();
        assertThatThrownBy(() -> authority.getAllowedTools().add(Tool.INCOME_READ))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> authority.getAllowedData().add(DataType.INCOME))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void permissionEntitiesAcceptEmptyImmutableSets() {
        EmployeeAuthority authority =
                new EmployeeAuthority(
                        "EMP-900",
                        EmployeeAuthorityStatus.ACTIVE,
                        CustomerScope.ALL,
                        Set.of(),
                        Set.of());
        PermissionTemplate template =
                new PermissionTemplate(
                        "EMPTY-TPL",
                        TaskType.LOAN_REVIEW,
                        Set.of(),
                        Set.of(),
                        60,
                        PermissionTemplateStatus.ACTIVE);
        ConsumerMandate mandate =
                new ConsumerMandate(
                        "CUST-900",
                        TaskType.LOAN_REVIEW,
                        ConsumerMandateStatus.ACTIVE,
                        Set.of());
        TaskPassport passport =
                new TaskPassport(
                        "EMPTY-PASS",
                        "LOAN-AGENT-01",
                        "EMP-900",
                        "CASE-900",
                        "CUST-900",
                        TaskType.LOAN_REVIEW,
                        Set.of(),
                        Set.of(),
                        TaskPassportStatus.ACTIVE,
                        ISSUED,
                        EXPIRES,
                        new SourceVersions(0L, 0L, 0L, 0L));

        assertThat(authority.getAllowedTools()).isEmpty();
        assertThat(authority.getAllowedData()).isEmpty();
        assertThat(template.getAllowedTools()).isEmpty();
        assertThat(template.getAllowedData()).isEmpty();
        assertThat(mandate.getAllowedData()).isEmpty();
        assertThat(passport.getAllowedTools()).isEmpty();
        assertThat(passport.getAllowedData()).isEmpty();
    }

    @Test
    void consumerMandateRejectsDuplicateConsumerAndPurpose() {
        em.persist(
                new ConsumerMandate(
                        "CUST-900",
                        TaskType.LOAN_REVIEW,
                        ConsumerMandateStatus.ACTIVE,
                        EnumSet.of(DataType.INCOME)));
        em.flush();

        ConsumerMandate duplicate =
                new ConsumerMandate(
                        "CUST-900",
                        TaskType.LOAN_REVIEW,
                        ConsumerMandateStatus.REVOKED,
                        EnumSet.of(DataType.DEBT));

        // 같은 (consumerId, purpose)가 둘이면 어느 쪽을 볼지 정할 수 없다. DB가 막아야 한다.
        // 어느 제약이 걸렸는지까지 확인한다 — 아무 오류나 나면 통과하는 단언은 아무것도 증명하지 않는다.
        //
        // IDENTITY 키라서 INSERT가 persist 시점에 바로 나간다. flush까지 미뤄지지 않는다.
        assertThatThrownBy(() -> em.persist(duplicate))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("uk_consumer_mandate_consumer_purpose");
    }

    @Test
    void consumerMandateRoundTrips() {
        ConsumerMandate mandate =
                new ConsumerMandate(
                        "CUST-900",
                        TaskType.LOAN_REVIEW,
                        ConsumerMandateStatus.ACTIVE,
                        EnumSet.of(DataType.CREDIT_SCORE, DataType.DEBT));
        em.persist(mandate);
        em.flush();
        em.clear();

        ConsumerMandate found = em.find(ConsumerMandate.class, mandate.getMandateId());

        assertThat(found.getConsumerId()).isEqualTo("CUST-900");
        assertThat(found.getPurpose()).isEqualTo(TaskType.LOAN_REVIEW);
        assertThat(found.isActive()).isTrue();
        assertThat(found.getAllowedData())
                .containsExactlyInAnyOrder(DataType.CREDIT_SCORE, DataType.DEBT);
        assertThat(found.getVersion()).isZero();
    }

    @Test
    void permissionTemplateRoundTrips() {
        em.clear();
        PermissionTemplate found = em.find(PermissionTemplate.class, "TPL-900");

        assertThat(found.getTaskType()).isEqualTo(TaskType.LOAN_REVIEW);
        assertThat(found.getAllowedTools()).containsExactlyInAnyOrderElementsOf(EnumSet.allOf(Tool.class));
        assertThat(found.getAllowedData())
                .containsExactlyInAnyOrderElementsOf(EnumSet.allOf(DataType.class));
        assertThat(found.getDefaultDurationMinutes()).isEqualTo(60);
        assertThat(found.isActive()).isTrue();
        assertThat(found.getTemplateId()).isEqualTo("TPL-900");
    }

    @Test
    void financialCasePreservesTheInstantAcrossTheRoundTrip() {
        em.persist(financialCase(FinancialCaseStatus.ACTIVE));
        em.flush();
        em.clear();

        FinancialCase found = em.find(FinancialCase.class, "CASE-900");

        // timestamptz 로 저장되므로 시점이 그대로 돌아와야 한다. docs/06 §3.
        assertThat(found.getIssuedAt()).isEqualTo(ISSUED);
        assertThat(found.getExpiresAt()).isEqualTo(EXPIRES);
        assertThat(found.getEmployeeId()).isEqualTo("EMP-900");
        assertThat(found.getConsumerId()).isEqualTo("CUST-900");
        assertThat(found.getTemplateId()).isEqualTo("TPL-900");
        assertThat(found.getTaskType()).isEqualTo(TaskType.LOAN_REVIEW);
        assertThat(found.getStatus()).isEqualTo(FinancialCaseStatus.ACTIVE);
        assertThat(found.getCaseId()).isEqualTo("CASE-900");
        assertThat(found.getVersion()).isZero();
    }

    @Test
    void financialCaseIsUnusableWhenInactiveOrExpired() {
        FinancialCase active = financialCase(FinancialCaseStatus.ACTIVE);
        FinancialCase cancelled = financialCase(FinancialCaseStatus.CANCELLED);

        assertThat(active.isUsableAt(ISSUED)).isTrue();
        assertThat(active.isUsableAt(EXPIRES.plusSeconds(1))).isFalse();
        assertThat(cancelled.isUsableAt(ISSUED)).isFalse();
    }

    @Test
    void taskPassportStoresSourceVersionsAsFourColumns() {
        em.persist(financialCase(FinancialCaseStatus.ACTIVE));
        em.persist(taskPassport(new SourceVersions(1L, 2L, 3L, 4L)));
        em.flush();
        em.clear();

        TaskPassport found = em.find(TaskPassport.class, "PASS-900");
        SourceVersions versions = found.getSourceVersions();

        assertThat(versions.getEmployeeAuthority()).isEqualTo(1L);
        assertThat(versions.getPermissionTemplate()).isEqualTo(2L);
        assertThat(versions.getFinancialCase()).isEqualTo(3L);
        assertThat(versions.getConsumerMandate()).isEqualTo(4L);
        assertThat(found.getAllowedTools()).containsExactly(Tool.CREDIT_SCORE_READ);
        assertThat(found.getAllowedData()).containsExactly(DataType.CREDIT_SCORE);
        assertThat(found.getAgentId()).isEqualTo("LOAN-AGENT-01");
        assertThat(found.getEmployeeId()).isEqualTo("EMP-900");
        assertThat(found.getCaseId()).isEqualTo("CASE-900");
        assertThat(found.getConsumerId()).isEqualTo("CUST-900");
        assertThat(found.getTaskType()).isEqualTo(TaskType.LOAN_REVIEW);
        assertThat(found.getPassportId()).isEqualTo("PASS-900");
        assertThat(found.getIssuedAt()).isEqualTo(ISSUED);
        assertThat(found.getExpiresAt()).isEqualTo(EXPIRES);
        assertThat(found.getStatus()).isEqualTo(TaskPassportStatus.ACTIVE);
        assertThat(found.isUsableAt(ISSUED)).isTrue();
    }

    @Test
    void sourceVersionsMatchOnlyWhenAllFourAgree() {
        SourceVersions issued = new SourceVersions(1L, 1L, 1L, 1L);

        assertThat(issued.matches(new SourceVersions(1L, 1L, 1L, 1L))).isTrue();
        assertThat(issued.matches(new SourceVersions(2L, 1L, 1L, 1L))).isFalse();
        assertThat(issued.matches(new SourceVersions(1L, 2L, 1L, 1L))).isFalse();
        assertThat(issued.matches(new SourceVersions(1L, 1L, 2L, 1L))).isFalse();
        assertThat(issued.matches(new SourceVersions(1L, 1L, 1L, 2L))).isFalse();
    }

    @Test
    void agentRunKeepsInputRefOrder() {
        em.persist(financialCase(FinancialCaseStatus.ACTIVE));
        em.persist(taskPassport(new SourceVersions(0L, 0L, 0L, 0L)));
        em.persist(agentRun(List.of("INPUT-001", "INPUT-002", "INPUT-003")));
        em.flush();
        em.clear();

        AgentRun found = em.find(AgentRun.class, "RUN-900");

        assertThat(found.getInputRefs()).containsExactly("INPUT-001", "INPUT-002", "INPUT-003");
        assertThat(found.getStatus()).isEqualTo(AgentRunStatus.RUNNING);
        assertThat(found.getAgentId()).isEqualTo("LOAN-AGENT-01");
        assertThat(found.getEmployeeId()).isEqualTo("EMP-900");
        assertThat(found.getCaseId()).isEqualTo("CASE-900");
        assertThat(found.getPassportId()).isEqualTo("PASS-900");
        assertThat(found.getAgentRunId()).isEqualTo("RUN-900");
        assertThat(found.getStartedAt()).isEqualTo(ISSUED);
    }

    @Test
    void securedInputStoresHashWithoutRawText() {
        em.persist(financialCase(FinancialCaseStatus.ACTIVE));
        em.persist(taskPassport(new SourceVersions(0L, 0L, 0L, 0L)));
        em.persist(agentRun(List.of("INPUT-900")));
        em.persist(new SecuredAgentInput("INPUT-900", "RUN-900", "sha256:abc", "ko", ISSUED));
        em.flush();
        em.clear();

        SecuredAgentInput found = em.find(SecuredAgentInput.class, "INPUT-900");

        assertThat(found.getInputHash()).isEqualTo("sha256:abc");
        assertThat(found.getContentLanguage()).isEqualTo("ko");
        assertThat(found.getAgentRunId()).isEqualTo("RUN-900");
        assertThat(found.getInputRef()).isEqualTo("INPUT-900");
        assertThat(found.getRegisteredAt()).isEqualTo(ISSUED);
    }

    @Test
    void promptRiskSnapshotDefaultsToNotEvaluated() {
        em.persist(financialCase(FinancialCaseStatus.ACTIVE));
        em.persist(taskPassport(new SourceVersions(0L, 0L, 0L, 0L)));
        em.persist(agentRun(List.of("INPUT-900")));
        em.persist(new SecuredAgentInput("INPUT-900", "RUN-900", "sha256:abc", "ko", ISSUED));
        PromptRiskSnapshot snapshot =
                PromptRiskSnapshot.notEvaluated("INPUT-900", "sha256:abc", "prompt-guard-4", ISSUED);
        em.persist(snapshot);
        em.flush();
        em.clear();

        PromptRiskSnapshot found = em.find(PromptRiskSnapshot.class, snapshot.getSnapshotId());

        // "검사하지 않았음"은 "검사했고 음성"과 반드시 구분돼야 한다. docs/04 §7.
        assertThat(found.getEvaluationStatus()).isEqualTo(PromptRiskEvaluationStatus.NOT_EVALUATED);
        assertThat(found.isDetected()).isFalse();
        assertThat(found.getPromptRisk()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(found.getAttackType()).isNull();
        assertThat(found.getMatchedRules()).isEmpty();
        assertThat(found.getModelVersion()).isEqualTo("prompt-guard-4");
        assertThat(found.getInputRef()).isEqualTo("INPUT-900");
        assertThat(found.getInputHash()).isEqualTo("sha256:abc");
        assertThat(found.getEvaluatedAt()).isEqualTo(ISSUED);
    }

    @Test
    void auditEventRejectsDuplicateRequestId() {
        em.persist(auditEvent("AUD-900", "REQ-900"));
        em.flush();

        em.persist(auditEvent("AUD-901", "REQ-900"));

        // docs/04 §17 — 같은 Request ID의 downstream 실행은 최대 1회여야 한다.
        // 이 제약이 그 보증의 근거다. 없으면 중복 금융 호출이 가능해진다.
        assertThatThrownBy(em::flush)
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("uk_audit_event_request_id");
    }

    @Test
    void auditEventStartsAsProcessingWithoutADecision() {
        AuditEvent event = auditEvent("AUD-900", "REQ-900");
        em.persist(event);
        em.flush();
        em.clear();

        AuditEvent found = em.find(AuditEvent.class, "AUD-900");

        assertThat(found.getStatus()).isEqualTo(AuditStatus.PROCESSING);
        assertThat(found.getDecision()).isNull();
        assertThat(found.getReasonCodes()).isEmpty();
        assertThat(found.getDownstreamReached()).isNull();
        assertThat(found.getResponseReleased()).isNull();
        assertThat(found.getSuccess()).isNull();
        assertThat(found.getRecordsRead()).isNull();
        assertThat(found.getLatencyMs()).isNull();
        assertThat(found.getCompletedAt()).isNull();
        assertThat(found.getPromptRisk()).isNull();
        assertThat(found.getBehaviorRisk()).isNull();
        assertThat(found.getPolicyVersion()).isNull();
        // Case·Tool은 판정 결과가 아니라 요청 시점에 이미 아는 값이라 PROCESSING에서도 채워져 있다.
        assertThat(found.getCaseId()).isEqualTo("LOAN-2026-900");
        assertThat(found.getTargetConsumerId()).isEqualTo("CUST-900");
        assertThat(found.getRequestedTool()).isEqualTo(Tool.CREDIT_SCORE_READ);
        assertThat(found.getTraceId()).isEqualTo("trace-900");
        assertThat(found.getAgentId()).isEqualTo("LOAN-AGENT-01");
        assertThat(found.getAgentRunId()).isEqualTo("RUN-900");
        assertThat(found.getRequestId()).isEqualTo("REQ-900");
        assertThat(found.getAuditEventId()).isEqualTo("AUD-900");
        assertThat(found.getRequestedAt()).isEqualTo(ISSUED);
    }

    @Test
    void auditEventReadsExecutionOutcomeNeededByBehaviorHistory() {
        em.persist(auditEvent("AUD-900", "REQ-900"));
        em.flush();

        // Outcome 갱신 API는 후속 Audit API 티켓에서 구현한다. 여기서는 V1 스키마와 JPA 매핑이
        // docs/04 §9의 Behavior History에 필요한 실행 결과를 보존하는지 고정한다.
        em.createNativeQuery(
                        "update audit_events"
                                + " set success = true, records_read = 1, latency_ms = 120"
                                + " where audit_event_id = 'AUD-900'")
                .executeUpdate();
        em.clear();

        AuditEvent found = em.find(AuditEvent.class, "AUD-900");

        assertThat(found.getSuccess()).isTrue();
        assertThat(found.getRecordsRead()).isEqualTo(1);
        assertThat(found.getLatencyMs()).isEqualTo(120L);
    }

    @Test
    void securityAuthEventAllowsRepeatedRequestId() {
        em.persist(securityAuthEvent("SEC-900"));
        em.persist(securityAuthEvent("SEC-901"));

        // 같은 요청으로 인증이 여러 번 실패하는 것은 정상이고, 그 반복이 관측해야 할 신호다.
        em.flush();
        em.clear();

        SecurityAuthEvent found = em.find(SecurityAuthEvent.class, "SEC-900");

        assertThat(found.getEventType()).isEqualTo(SecurityEventType.AUTH_FAILURE);
        assertThat(found.getReasonCode()).isEqualTo("AGENT_AUTHENTICATION_FAILED");
        assertThat(found.getCredentialType()).isEqualTo("AGENT_SERVICE");
        assertThat(found.getSourceFingerprint()).isEqualTo("sha256:non-pii");
        assertThat(found.getRequestId()).isEqualTo("REQ-900");
        assertThat(found.getTraceId()).isEqualTo("trace-900");
        assertThat(found.getSecurityEventId()).isEqualTo("SEC-900");
        assertThat(found.getOccurredAt()).isEqualTo(ISSUED);
    }

    @Test
    void identifierAnchorsRoundTrip() {
        em.clear();

        assertThat(em.find(Employee.class, "EMP-900").getCreatedAt()).isEqualTo(ISSUED);
        assertThat(em.find(Employee.class, "EMP-900").getEmployeeId()).isEqualTo("EMP-900");
        assertThat(em.find(Consumer.class, "CUST-900").getCreatedAt()).isEqualTo(ISSUED);
        assertThat(em.find(Consumer.class, "CUST-900").getConsumerId()).isEqualTo("CUST-900");
    }

    private FinancialCase financialCase(FinancialCaseStatus status) {
        return new FinancialCase(
                "CASE-900",
                "EMP-900",
                "CUST-900",
                TaskType.LOAN_REVIEW,
                "TPL-900",
                status,
                ISSUED,
                EXPIRES);
    }

    private TaskPassport taskPassport(SourceVersions versions) {
        return new TaskPassport(
                "PASS-900",
                "LOAN-AGENT-01",
                "EMP-900",
                "CASE-900",
                "CUST-900",
                TaskType.LOAN_REVIEW,
                Set.of(Tool.CREDIT_SCORE_READ),
                Set.of(DataType.CREDIT_SCORE),
                TaskPassportStatus.ACTIVE,
                ISSUED,
                EXPIRES,
                versions);
    }

    private AgentRun agentRun(List<String> inputRefs) {
        return new AgentRun(
                "RUN-900",
                "LOAN-AGENT-01",
                "EMP-900",
                "CASE-900",
                "PASS-900",
                inputRefs,
                AgentRunStatus.RUNNING,
                ISSUED);
    }

    private AuditEvent auditEvent(String auditEventId, String requestId) {
        return new AuditEvent(
                auditEventId,
                requestId,
                "trace-900",
                "LOAN-AGENT-01",
                "RUN-900",
                "LOAN-2026-900",
                "CUST-900",
                Tool.CREDIT_SCORE_READ,
                ISSUED);
    }

    private SecurityAuthEvent securityAuthEvent(String securityEventId) {
        return new SecurityAuthEvent(
                securityEventId,
                "REQ-900",
                "trace-900",
                SecurityEventType.AUTH_FAILURE,
                "AGENT_AUTHENTICATION_FAILED",
                "AGENT_SERVICE",
                "sha256:non-pii",
                ISSUED);
    }
}
