package io.finguard.core.domain;

/** Gateway 보안 Event. Business Audit과 분리한다. docs/06-common-conventions.md §10. */
public enum SecurityEventType {
    AUTH_FAILURE,
    RATE_LIMITED,
}
