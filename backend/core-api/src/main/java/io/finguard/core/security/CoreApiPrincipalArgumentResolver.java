package io.finguard.core.security;

import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Controller가 {@link CoreApiPrincipal}을 파라미터로 바로 받게 한다.
 *
 * <p>Request attribute 키를 Controller마다 꺼내 쓰면 그 문자열이 코드 곳곳에 퍼지고, 한 군데서 오타가
 * 나면 {@code null}이 조용히 흘러 들어간다. 꺼내는 곳을 여기 하나로 묶는다.
 */
public class CoreApiPrincipalArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return CoreApiPrincipal.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        CoreApiPrincipal principal =
                request == null
                        ? null
                        : (CoreApiPrincipal)
                                request.getAttribute(CoreApiCredentialFilter.PRINCIPAL_ATTRIBUTE);

        if (principal == null) {
            // 필터가 지키지 않는 경로의 Controller가 Principal을 받으려 한 것이다. 배선 실수이므로
            // null을 넘겨 인증된 것처럼 보이게 두지 않는다.
            throw new IllegalStateException(
                    "인증된 Principal 없이 " + parameter.getMethod() + " 가 호출됐습니다. 필터 등록 범위를 확인하세요.");
        }
        return principal;
    }
}
