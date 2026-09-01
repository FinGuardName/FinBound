package io.finguard.core.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * 자격 증명 검증 자체의 단위 테스트.
 *
 * <p><strong>경로 범위는 여기서 검증하지 않는다.</strong> 어떤 경로가 이 필터를 거치는지는 서블릿
 * 등록 패턴이 정하며, 그건 실제 컨테이너를 지나가는 {@code InternalCredentialFilterHttpTest}가
 * 검증한다. 필터 안에서 경로를 다시 판정하던 코드가 인증 우회를 만들었기 때문에 제거했다.
 */
@ExtendWith(OutputCaptureExtension.class)
class InternalCredentialFilterTest {

    private static final String EXPECTED_CREDENTIAL = "expected-internal-credential";

    private static final String PRESENTED_WRONG_CREDENTIAL = "attacker-supplied-guess";

    private final InternalCredentialFilter filter = new InternalCredentialFilter(EXPECTED_CREDENTIAL);

    @Test
    void rejectsInternalRequestWithoutCredentialHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/v1/context/resolve");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("INTERNAL_CREDENTIAL_INVALID");
        // 자격 증명이 없으면 downstream 핸들러에 도달하지 않는다.
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void rejectsInternalRequestWithWrongCredential() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/v1/audits");
        request.addHeader(InternalCredentialFilter.CREDENTIAL_HEADER, "wrong-credential");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void logsRejectionWithoutLeakingAnyCredentialValue(CapturedOutput output) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/v1/audits");
        request.addHeader(InternalCredentialFilter.CREDENTIAL_HEADER, PRESENTED_WRONG_CREDENTIAL);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        // 거절이 관측 가능해야 한다. 지금은 401만 나가고 흔적이 없어 유출·추측 시도가 보이지 않는다.
        assertThat(output).contains("INTERNAL_CREDENTIAL_INVALID");
        assertThat(output).contains("/internal/v1/audits");
        // docs/06 §26 — Credential은 출력하지 않는다. 제시된 값도 기대값도 로그에 남지 않아야 한다.
        assertThat(output).doesNotContain(PRESENTED_WRONG_CREDENTIAL);
        assertThat(output).doesNotContain(EXPECTED_CREDENTIAL);
    }

    @Test
    void passesInternalRequestWithMatchingCredential() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/v1/audits");
        request.addHeader(InternalCredentialFilter.CREDENTIAL_HEADER, EXPECTED_CREDENTIAL);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isSameAs(request);
    }

}
