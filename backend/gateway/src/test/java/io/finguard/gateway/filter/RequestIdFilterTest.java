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
        assertThat(generated).isNotBlank();
        assertThat(response.getHeader(RequestIdFilter.HEADER_REQUEST_ID)).isEqualTo(generated);
    }

    @Test
    void existingHeaderIsPreserved() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/gateway/v1/tool-calls");
        request.addHeader(RequestIdFilter.HEADER_REQUEST_ID, "REQ-preset");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(request.getAttribute(RequestIdFilter.ATTRIBUTE_REQUEST_ID)).isEqualTo("REQ-preset");
        assertThat(response.getHeader(RequestIdFilter.HEADER_REQUEST_ID)).isEqualTo("REQ-preset");
    }
}
