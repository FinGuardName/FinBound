package io.finguard.core.security;

import java.util.Set;

import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import io.finguard.core.domain.ReasonCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 인증된 호출자의 Role이 이 핸들러에 열려 있는지 본다. {@code docs/04-api-contract.md} §2.
 *
 * <p><strong>경로를 문자열로 다시 판정하지 않는다.</strong> 판정 기준은 두 가지뿐이다 —
 * {@link CoreApiCredentialFilter}가 심어 둔 Principal이 있는지, 그리고 Spring이 이미 골라 준 핸들러가
 * 어떤 Role을 밝혔는지. Principal의 존재 여부가 곧 "이 요청이 브라우저 표면인가"의 답이므로 경로 판정의
 * 진실은 필터의 등록 패턴 한 곳에만 남는다.
 *
 * <p>필터가 아니라 Interceptor인 이유도 같다. 필터 시점에는 어느 핸들러로 갈지 아직 정해지지 않아
 * method와 path를 손으로 다시 맞춰야 하는데, 그 재파싱이 바로 {@link CoreApiCredentialFilter} Javadoc이
 * 경고하는 우회 경로다. Interceptor는 매핑이 끝난 뒤라 핸들러를 그대로 받는다.
 */
public class CoreApiRoleInterceptor implements HandlerInterceptor {

    static final ReasonCode REASON_CODE = ReasonCode.CORE_API_ROLE_FORBIDDEN;

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler) {
        CoreApiPrincipal principal =
                (CoreApiPrincipal) request.getAttribute(CoreApiCredentialFilter.PRINCIPAL_ATTRIBUTE);
        if (principal == null) {
            // 필터가 지키는 범위 밖이다. /internal/v1/** 은 서비스 간 Credential이 따로 지킨다.
            return true;
        }

        if (!(handler instanceof HandlerMethod handlerMethod)) {
            // Principal이 있다는 건 /api/v1 요청이라는 뜻인데 Controller가 아닌 무언가로 가고 있다.
            // 무엇인지 모르는 채 통과시키지 않는다.
            throw new CoreApiAccessDeniedException(
                    REASON_CODE, "핸들러가 Controller 메서드가 아니라 권한을 확인할 수 없습니다.");
        }

        Set<CoreApiRole> allowed = ApiRoleDeclarations.declaredRoles(handlerMethod);
        // 선언이 없으면 빈 집합이고 contains는 거짓이다. 기동 검증이 이 경우를 이미 막지만,
        // 여기서도 닫히는 쪽으로 떨어지게 둔다.
        if (!allowed.contains(principal.role())) {
            throw new CoreApiAccessDeniedException(
                    REASON_CODE, handlerMethod.getShortLogMessage() + " 는 이 Role에게 열려 있지 않습니다.");
        }
        return true;
    }
}
