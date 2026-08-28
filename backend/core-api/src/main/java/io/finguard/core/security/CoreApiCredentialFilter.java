package io.finguard.core.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Enumeration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

import io.finguard.core.domain.ReasonCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 브라우저(Vue) → Core {@code /api/v1/**}의 opaque Bearer Credential을 검증한다.
 * {@code docs/04-api-contract.md} §2.
 *
 * <p>경로 판정은 <strong>오직 서블릿 등록 패턴으로만</strong> 한다. 필터 안에서
 * {@code getRequestURI()}로 다시 검사하지 않는다 — 그 값은 디코딩·정규화 이전이라 컨테이너가 보는
 * 경로와 어긋날 수 있고, 두 판정이 갈리는 순간 필터가 스스로 비켜서는 우회가 생긴다.
 * {@link InternalCredentialFilter}에서 실제로 그런 우회가 있었다.
 */
public class CoreApiCredentialFilter extends OncePerRequestFilter {

    /** 서블릿 등록용 패턴. */
    public static final String API_URL_PATTERN = "/api/v1/*";

    /** 검증을 통과한 호출자를 담는 request attribute. */
    public static final String PRINCIPAL_ATTRIBUTE = CoreApiCredentialFilter.class.getName() + ".principal";

    /**
     * 인가 거부 사유를 필터까지 되돌려 보내는 request attribute.
     *
     * <p>403은 여기서 나지 않는다 — Interceptor나 Controller가 예외를 던지고
     * {@code CoreApiExceptionHandler}가 응답을 만든다. 그 사유를 이 칸에 적어 두면 필터가 기록할 때
     * 집어갈 수 있다.
     */
    public static final String DENIED_REASON_ATTRIBUTE =
            CoreApiCredentialFilter.class.getName() + ".deniedReason";

    private static final Logger log = LoggerFactory.getLogger(CoreApiCredentialFilter.class);

    private static final String BEARER_PREFIX = "Bearer ";
    private static final ReasonCode REASON_CODE = ReasonCode.CORE_API_CREDENTIAL_INVALID;

    private final byte[] viewerDigest;
    private final byte[] operatorDigest;
    private final String operatorEmployeeId;
    private final CoreApiAuthEventRecorder recorder;

    public CoreApiCredentialFilter(CoreApiProperties properties, CoreApiAuthEventRecorder recorder) {
        this.viewerDigest = sha256(properties.viewerCredential());
        this.operatorDigest = sha256(properties.operatorCredential());
        this.operatorEmployeeId = properties.operatorEmployeeId();
        this.recorder = recorder;
    }

    /**
     * 거부 기록을 여기 한 곳에서 한다.
     *
     * <p>401은 이 필터가, 403은 Interceptor와 Controller가 낸다. 각자 기록하게 두면 새 거부 경로가
     * 생길 때마다 기록을 빠뜨릴 자리가 하나씩 늘고, {@code requestId}에 UNIQUE가 없어
     * ({@code SecurityAuthEvent} Javadoc) 중복도 DB가 막아주지 않는다. 응답이 완성된 뒤 상태 코드를
     * 보고 적으면 경로가 몇 개든 여기를 지난다.
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        CoreApiPrincipal principal = authenticate(request);
        if (principal == null) {
            reject(request, response);
            recorder.record(request, REASON_CODE);
            return;
        }
        request.setAttribute(PRINCIPAL_ATTRIBUTE, principal);

        filterChain.doFilter(request, response);

        if (response.getStatus() == HttpServletResponse.SC_FORBIDDEN) {
            Object reason = request.getAttribute(DENIED_REASON_ATTRIBUTE);
            // 사유를 못 받았어도 403이 났다는 사실 자체는 남긴다. 모르는 채 비워 두는 것보다 낫다.
            recorder.record(
                    request,
                    reason instanceof ReasonCode denied ? denied : ReasonCode.CORE_API_ROLE_FORBIDDEN);
        }
    }

    private CoreApiPrincipal authenticate(HttpServletRequest request) {
        String presented = bearerToken(request);
        if (presented == null) {
            return null;
        }

        // 두 Credential을 조건 분기 없이 모두 비교한다. if/else로 짜면 어느 쪽에서 걸렸는지가
        // 응답 시간으로 새어나간다. 비교 대상은 길이가 고정된 SHA-256 다이제스트라 길이 자체도 신호가 되지 않는다.
        byte[] presentedDigest = sha256(presented);
        boolean isViewer = MessageDigest.isEqual(viewerDigest, presentedDigest);
        boolean isOperator = MessageDigest.isEqual(operatorDigest, presentedDigest);

        if (isOperator) {
            return new CoreApiPrincipal(CoreApiRole.OPERATOR, operatorEmployeeId);
        }
        if (isViewer) {
            return new CoreApiPrincipal(CoreApiRole.VIEWER, null);
        }
        return null;
    }

    /**
     * {@code Authorization: Bearer <token>} 정확히 하나만 받는다.
     *
     * <p>헤더가 여러 개면 어느 것이 유효한지 컨테이너·프록시마다 다르게 고를 수 있으므로 거부한다.
     */
    private String bearerToken(HttpServletRequest request) {
        Enumeration<String> headers = request.getHeaders(HttpHeaders.AUTHORIZATION);
        if (headers == null) {
            return null;
        }
        List<String> values = java.util.Collections.list(headers);
        if (values.size() != 1) {
            return null;
        }

        String value = values.get(0);
        if (!value.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = value.substring(BEARER_PREFIX.length());
        return token.isEmpty() ? null : token;
    }

    private void reject(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // 제시된 값도 기대값도 남기지 않는다 — docs/06-common-conventions.md §26.
        // 같은 §26이 Request ID / Trace ID는 포함하라고 한다. 그게 있어야 이 거절이 어느 요청이었는지
        // 다른 로그와 이어붙일 수 있다.
        log.warn(
                "Core API credential rejected. reasonCode={} method={} path={} requestId={} traceId={}",
                REASON_CODE,
                request.getMethod(),
                request.getRequestURI(),
                request.getHeader("X-Request-Id"),
                request.getHeader("Traceparent"));

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        // RFC 9110 §11.6.1 — 401은 어떤 인증을 기대하는지 밝혀야 한다. realm은 붙이지 않는다.
        // 무엇을 지키는 문인지 알려주는 것뿐이고, 여기서는 이름 지을 만한 구획이 하나뿐이다.
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        response.setContentType("application/problem+json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"reasonCode\":\"" + REASON_CODE.name() + "\"}");
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required to compare credentials", exception);
        }
    }
}
