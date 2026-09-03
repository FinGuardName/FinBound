package io.finguard.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.FilterChain;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    void missingHeaderGeneratesUuidAndEchoesInResponse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/gateway/v1/tool-calls");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        String generated = (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_REQUEST_ID);
        String traceparent = (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_TRACEPARENT);
        assertThat(generated).isNotBlank();
        assertThat(traceparent).matches("^00-[0-9a-f]{32}-[0-9a-f]{16}-01$");
        assertThat(response.getHeader(RequestIdFilter.HEADER_REQUEST_ID)).isEqualTo(generated);
        assertThat(response.getHeader(RequestIdFilter.HEADER_TRACEPARENT)).isEqualTo(traceparent);
    }

    @Test
    void existingHeaderIsPreserved() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/gateway/v1/tool-calls");
        request.addHeader(RequestIdFilter.HEADER_REQUEST_ID, "550e8400-e29b-41d4-a716-446655440000");
        request.addHeader(RequestIdFilter.HEADER_TRACEPARENT,
            "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(request.getAttribute(RequestIdFilter.ATTRIBUTE_REQUEST_ID))
            .isEqualTo("550e8400-e29b-41d4-a716-446655440000");
        assertThat(request.getAttribute(RequestIdFilter.ATTRIBUTE_TRACEPARENT))
            .isEqualTo("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
        assertThat(response.getHeader(RequestIdFilter.HEADER_REQUEST_ID))
            .isEqualTo("550e8400-e29b-41d4-a716-446655440000");
        assertThat(response.getHeader(RequestIdFilter.HEADER_TRACEPARENT))
            .isEqualTo("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
    }

    @Test
    void nonUuidRequestIdIsRejected() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/gateway/v1/tool-calls");
        request.addHeader(RequestIdFilter.HEADER_REQUEST_ID, "REQ-preset");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("INVALID_TOOL_REQUEST");
    }
}
