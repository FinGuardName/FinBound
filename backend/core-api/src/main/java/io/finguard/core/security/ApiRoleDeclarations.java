package io.finguard.core.security;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

/**
 * {@code /api/v1/**} 핸들러가 자신에게 열린 Role을 밝혔는지 확인한다.
 * {@code docs/04-api-contract.md} §2.
 *
 * <p>허용목록을 한곳에 모아 두는 대신 각 핸들러가 스스로 밝히게 한 이유는 <strong>빠뜨림의 방향</strong>
 * 때문이다. 중앙 목록은 새 Endpoint가 목록에 오르지 않으면 목록이 조용히 낡고, 여기서는 권한을 적지
 * 않은 핸들러가 있으면 기동이 멈춘다. 어느 쪽이든 사람은 잊어버리므로, 잊었을 때 닫히는 쪽을 고른다.
 */
public final class ApiRoleDeclarations {

    /**
     * 브라우저를 향한 표면. {@link CoreApiCredentialFilter#API_URL_PATTERN}이 지키는 범위와 같아야 한다.
     *
     * <p>서블릿 패턴 {@code /api/v1/*}는 {@code /api/v1} 자체도 매칭하므로 여기서도 함께 본다.
     */
    private static final String API_PREFIX = "/api/v1";

    private ApiRoleDeclarations() {
    }

    /**
     * 권한을 밝히지 않은 {@code /api/v1} 핸들러가 있으면 기동을 멈춘다.
     *
     * @throws IllegalStateException 권한 선언이 없는 핸들러가 하나라도 있을 때
     */
    public static void verifyEveryApiHandlerDeclaresRoles(
            Map<RequestMappingInfo, HandlerMethod> handlers) {
        List<String> undeclared = new ArrayList<>();
        handlers.forEach(
                (mapping, handler) -> {
                    if (facesTheBrowser(mapping) && declaredRoles(handler).isEmpty()) {
                        undeclared.add(mapping + " -> " + handler.getShortLogMessage());
                    }
                });

        if (undeclared.isEmpty()) {
            return;
        }
        Collections.sort(undeclared);
        throw new IllegalStateException(
                "다음 /api/v1 핸들러가 @RequiresRole 을 밝히지 않았습니다. 권한을 적지 않은 Endpoint는 열 수 없습니다: "
                        + String.join(", ", undeclared));
    }

    /** 핸들러가 밝힌 Role. 선언이 없으면 빈 집합이고, 빈 집합은 "아무에게도 열리지 않음"으로 읽힌다. */
    public static Set<CoreApiRole> declaredRoles(HandlerMethod handler) {
        RequiresRole declaration = handler.getMethodAnnotation(RequiresRole.class);
        if (declaration == null) {
            return Set.of();
        }
        EnumSet<CoreApiRole> roles = EnumSet.noneOf(CoreApiRole.class);
        Collections.addAll(roles, declaration.value());
        return Collections.unmodifiableSet(roles);
    }

    private static boolean facesTheBrowser(RequestMappingInfo mapping) {
        return mapping.getPatternValues().stream()
                .anyMatch(pattern -> pattern.equals(API_PREFIX) || pattern.startsWith(API_PREFIX + "/"));
    }
}
