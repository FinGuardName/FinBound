package io.finguard.core.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpServletRequest;

import io.finguard.core.domain.ReasonCode;
import io.finguard.core.securityevent.SecurityEventService;

/**
 * 거부 기록의 실패가 거부 <strong>결정</strong>을 뒤집지 못한다는 것을 못박는다.
 *
 * <p>이 성질이 깨지면 기록 장애가 그대로 인증 우회가 된다 — 저장소가 아프면 문이 열리는 셈이다.
 */
class CoreApiAuthEventRecorderTest {

    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-08-26T09:00:00Z"), ZoneOffset.UTC);

    @Test
    void neverLetsAWriteFailureEscape() {
        SecurityEventService failing = mock(SecurityEventService.class);
        doThrow(new DataIntegrityViolationException("write failed"))
                .when(failing)
                .recordCoreApiAuthFailure(any(), any(), any(), any());
        CoreApiAuthEventRecorder recorder = new CoreApiAuthEventRecorder(failing, FIXED);

        assertThatCode(
                        () ->
                                recorder.record(
                                        new MockHttpServletRequest(),
                                        ReasonCode.CORE_API_CREDENTIAL_INVALID))
                .doesNotThrowAnyException();
    }

    /** 브라우저에서 오는 경로에는 Gateway가 없다. 없으면 Core가 만든다 — {@code docs/04} §2. */
    @Test
    void generatesARequestIdWhenTheHeaderIsAbsent() {
        SecurityEventService events = mock(SecurityEventService.class);
        CoreApiAuthEventRecorder recorder = new CoreApiAuthEventRecorder(events, FIXED);

        recorder.record(new MockHttpServletRequest(), ReasonCode.CORE_API_ROLE_FORBIDDEN);

        ArgumentCaptor<String> requestId = ArgumentCaptor.forClass(String.class);
        verify(events)
                .recordCoreApiAuthFailure(
                        requestId.capture(),
                        any(),
                        eq(ReasonCode.CORE_API_ROLE_FORBIDDEN),
                        eq(FIXED.instant()));
        assertThat(requestId.getValue()).isNotBlank();
    }

    /**
     * {@code request_id} 컬럼은 {@code varchar(64)}다. 인증되지 않은 클라이언트가 보낸 긴 헤더 하나로
     * 기록 자체를 막을 수 있게 두지 않는다.
     */
    @Test
    void trimsAnOverlongRequestIdInsteadOfLosingTheRecord() {
        SecurityEventService events = mock(SecurityEventService.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", "x".repeat(200));
        CoreApiAuthEventRecorder recorder = new CoreApiAuthEventRecorder(events, FIXED);

        recorder.record(request, ReasonCode.EMPLOYEE_IDENTITY_MISMATCH);

        ArgumentCaptor<String> requestId = ArgumentCaptor.forClass(String.class);
        verify(events).recordCoreApiAuthFailure(requestId.capture(), any(), any(), any());
        assertThat(requestId.getValue()).hasSize(64);
    }
}
