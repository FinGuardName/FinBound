package io.finguard.core.identifier;

import java.util.Locale;
import java.util.UUID;

/** Audit 및 Security Event 기본키 생성기. 식별자에 업무 의미나 인증 정보를 싣지 않는다. */
public final class RecordIdentifiers {

    private RecordIdentifiers() {
    }

    public static String auditEventId() {
        return identifier("AUD-");
    }

    public static String securityEventId() {
        return identifier("SEC-");
    }

    private static String identifier(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
    }
}
