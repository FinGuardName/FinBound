package io.finguard.core.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

/**
 * {@code /api/v1/**} 핸들러는 자신이 어느 Role에게 열리는지 스스로 밝혀야 한다 —
 * {@code docs/04-api-contract.md} §2.
 *
 * <p>허용목록을 필터 안 문자열 표로 두면 <strong>새 Endpoint가 표에 오르지 않은 채 배포된다.</strong>
 * 그래서 판정을 뒤집었다. 권한을 적지 않은 핸들러가 하나라도 있으면 기동 자체를 막는다. 잊어버리는
 * 쪽이 열리는 게 아니라 닫히는 쪽이 되게 하는 것이 요점이다.
 */
class ApiRoleDeclarationsTest {

    /**
     * 권한을 적지 않은 {@code /api/v1} 핸들러는 기동을 멈춰 세운다.
     *
     * <p>런타임 403으로 막는 것과 다르다. 403은 누군가 그 경로를 두드려야 드러나지만, 기동 실패는
     * 배포 전에 드러난다.
     */
    @Test
    void refusesToStartWhenAnApiHandlerDeclaresNoRoles() {
        Map<RequestMappingInfo, HandlerMethod> handlers =
                Map.of(get("/api/v1/silently-open"), handler("undeclared"));

        assertThatThrownBy(() -> ApiRoleDeclarations.verifyEveryApiHandlerDeclaresRoles(handlers))
                .isInstanceOf(IllegalStateException.class)
                // 어디를 고쳐야 하는지 메시지가 알려주지 않으면 기동 실패는 수수께끼가 된다.
                .hasMessageContaining("/api/v1/silently-open");
    }

    @Test
    void startsWhenEveryApiHandlerDeclaresItsRoles() {
        Map<RequestMappingInfo, HandlerMethod> handlers =
                Map.of(get("/api/v1/declared"), handler("operatorOnly"));

        assertThatCode(() -> ApiRoleDeclarations.verifyEveryApiHandlerDeclaresRoles(handlers))
                .doesNotThrowAnyException();
    }

    /** {@code /internal/v1/**}은 서비스 간 Credential로 지켜지는 다른 표면이다. 여기서 Role을 요구하지 않는다. */
    @Test
    void leavesHandlersOutsideTheBrowserFacingSurfaceAlone() {
        Map<RequestMappingInfo, HandlerMethod> handlers =
                Map.of(get("/internal/v1/context/resolve"), handler("undeclared"));

        assertThatCode(() -> ApiRoleDeclarations.verifyEveryApiHandlerDeclaresRoles(handlers))
                .doesNotThrowAnyException();
    }

    @Test
    void readsTheRolesAHandlerDeclares() {
        assertThat(ApiRoleDeclarations.declaredRoles(handler("operatorOnly")))
                .containsExactly(CoreApiRole.OPERATOR);
    }

    /** 선언이 없으면 빈 집합이다. null이 아니다 — 호출자가 "못 찾음"과 "아무에게도 안 열림"을 구분할 이유가 없다. */
    @Test
    void reportsNoRolesForAnUndeclaredHandler() {
        assertThat(ApiRoleDeclarations.declaredRoles(handler("undeclared"))).isEmpty();
    }

    private static RequestMappingInfo get(String path) {
        return RequestMappingInfo.paths(path).methods(RequestMethod.GET).build();
    }

    private static HandlerMethod handler(String methodName) {
        try {
            Method method = SampleController.class.getDeclaredMethod(methodName);
            return new HandlerMethod(new SampleController(), method);
        } catch (NoSuchMethodException exception) {
            throw new IllegalStateException(exception);
        }
    }

    /** 검증기에 먹일 핸들러 표본. 매핑 어노테이션은 필요 없다 — 경로는 RequestMappingInfo가 들고 있다. */
    static class SampleController {

        @RequiresRole(CoreApiRole.OPERATOR)
        void operatorOnly() {
        }

        void undeclared() {
        }
    }
}
