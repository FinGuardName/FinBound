package io.finguard.core.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 이 핸들러가 어느 Role에게 열리는지 밝힌다. {@code docs/04-api-contract.md} §2.
 *
 * <p>{@code /api/v1/**}로 매핑된 핸들러는 <strong>빠짐없이</strong> 이 어노테이션을 달아야 하며,
 * 하나라도 빠지면 기동에 실패한다({@link ApiRoleDeclarations}). 허용목록을 한곳에 모아두면 새 Endpoint가
 * 목록에 오르지 않은 채 배포되지만, 이렇게 두면 권한을 적지 않는 순간 앱이 뜨지 않는다.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RequiresRole {

    /** 이 핸들러를 호출할 수 있는 Role. 비우면 아무도 호출할 수 없다. */
    CoreApiRole[] value();
}
