package io.finguard.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.FilterChain;

class RateLimitFilterTest {

    @Test
    void withinCapacityPasses() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(3, 1);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void exceedingCapacityReturns429() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(2, 1);
        MockHttpServletResponse response = null;
        for (int i = 0; i < 3; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("10.0.0.2");
            response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
        }
        assertThat(response.getStatus()).isEqualTo(429);
    }

    @Test
    void forwardedForHeaderIsUsedAsKey() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(1, 1);
        MockHttpServletRequest firstRequest = new MockHttpServletRequest();
        firstRequest.setRemoteAddr("10.0.0.9");
        firstRequest.addHeader("X-Forwarded-For", "203.0.113.5, 10.0.0.1");
        MockHttpServletResponse resp1 = new MockHttpServletResponse();
        filter.doFilter(firstRequest, resp1, new MockFilterChain());
        assertThat(resp1.getStatus()).isEqualTo(200);

        MockHttpServletRequest secondRequest = new MockHttpServletRequest();
        secondRequest.setRemoteAddr("10.0.0.9");
        secondRequest.addHeader("X-Forwarded-For", "203.0.113.5");
        MockHttpServletResponse resp2 = new MockHttpServletResponse();
        filter.doFilter(secondRequest, resp2, new MockFilterChain());
        assertThat(resp2.getStatus()).isEqualTo(429);
    }
}
