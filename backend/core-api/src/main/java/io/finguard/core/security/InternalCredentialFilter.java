package io.finguard.core.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Core Internal API의 서비스 간 자격 증명을 검증한다.
 *
 * <p>docs/04-api-contract.md §2 — Gateway는 {@code X-FinGuard-Service-Credential}을 붙여 호출한다.
 */
public class InternalCredentialFilter extends OncePerRequestFilter {

    public static final String CREDENTIAL_HEADER = "X-FinGuard-Service-Credential";

    /** 이 필터가 지키는 경로. */
    public static final String INTERNAL_PATH_PREFIX = "/internal/";

    /**
     * 서블릿 등록용 패턴. 경로 판정은 <strong>오직 이 등록 패턴으로만</strong> 한다.
     *
     * <p>필터 안에서 {@code getRequestURI()}로 경로를 다시 검사하지 않는다. 그 값은 디코딩·정규화
     * 이전의 원본이라 컨테이너·DispatcherServlet이 보는 경로와 다르고, 두 판정이 어긋나는 순간
     * 필터가 스스로 비켜서는 우회가 생긴다. {@code /%69nternal/v1/audits} 와
     * {@code /internal;x=1/v1/audits} 가 실제로 그렇게 인증을 통과했다.
     * 회귀 테스트는 {@code InternalCredentialFilterHttpTest}에 있다.
     */
    public static final String INTERNAL_URL_PATTERN = INTERNAL_PATH_PREFIX + "*";

    private static final Logger log = LoggerFactory.getLogger(InternalCredentialFilter.class);

    private static final String REASON_CODE = "INTERNAL_CREDENTIAL_INVALID";

    private final String expectedCredential;

    public InternalCredentialFilter(String expectedCredential) {
        this.expectedCredential = expectedCredential;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!matchesExpected(request.getHeader(CREDENTIAL_HEADER))) {
            reject(request, response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean matchesExpected(String presented) {
        if (presented == null) {
            return false;
        }
        // 자격 증명 비교는 길이에 따라 조기 반환하지 않는다.
        return MessageDigest.isEqual(
                presented.getBytes(StandardCharsets.UTF_8),
                expectedCredential.getBytes(StandardCharsets.UTF_8));
    }

    private void reject(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // 제시된 자격 증명 값도, 기대값도 남기지 않는다 — docs/06-common-conventions.md §26.
        // 남기는 것은 "언제 어느 경로가 거절됐는가"뿐이다. 유출·추측 시도를 관측할 최소한의 신호다.
        log.warn(
                "Internal API credential rejected. reasonCode={} method={} path={}",
                REASON_CODE,
                request.getMethod(),
                request.getRequestURI());

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"reasonCode\":\"" + REASON_CODE + "\"}");
    }
}
