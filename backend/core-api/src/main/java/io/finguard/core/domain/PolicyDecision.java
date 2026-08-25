package io.finguard.core.domain;

/** docs/06-common-conventions.md §11. 시스템 장애는 여기에 ERROR를 추가하지 않고 Audit 상태로 표현한다. */
public enum PolicyDecision {
    ALLOW,
    BLOCK,
}
