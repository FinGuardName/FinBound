package io.finguard.gateway.filter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER_REQUEST_ID = "X-Request-Id";
    public static final String HEADER_TRACEPARENT = "Traceparent";
    public static final String ATTRIBUTE_REQUEST_ID = "finguard.requestId";
    public static final String ATTRIBUTE_TRACEPARENT = "finguard.traceparent";
    private static final String MDC_KEY = "requestId";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String requestId = request.getHeader(HEADER_REQUEST_ID);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        } else if (!isUuid(requestId)) {
            rejectInvalidRequestId(response);
            return;
        }
        String traceparent = request.getHeader(HEADER_TRACEPARENT);
        if (traceparent == null || traceparent.isBlank()) {
            traceparent = newTraceparent();
        }
        request.setAttribute(ATTRIBUTE_REQUEST_ID, requestId);
        request.setAttribute(ATTRIBUTE_TRACEPARENT, traceparent);
        response.setHeader(HEADER_REQUEST_ID, requestId);
        response.setHeader(HEADER_TRACEPARENT, traceparent);
        MDC.put(MDC_KEY, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private String newTraceparent() {
        byte[] traceId = new byte[16];
        byte[] spanId = new byte[8];
        RANDOM.nextBytes(traceId);
        RANDOM.nextBytes(spanId);
        return "00-" + HEX.formatHex(traceId) + "-" + HEX.formatHex(spanId) + "-01";
    }

    private boolean isUuid(String requestId) {
        try {
            UUID.fromString(requestId);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private void rejectInvalidRequestId(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"reasonCode\":\"INVALID_TOOL_REQUEST\"}");
    }
}
