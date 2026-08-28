package io.finguard.core.agentrun;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

/**
 * 식별자와 입력 해시 생성. {@code docs/06-common-conventions.md} §2.
 *
 * <p>§2가 보인 {@code PASS-001} 형태는 "예시 형식"이므로 접두사만 지키고 뒤는 충돌하지 않는 값을 쓴다.
 * 순번을 쓰려면 시퀀스가 필요한데, 식별자에 의미를 싣지 않는다는 §2의 방침상 값어치가 없다 —
 * ID는 식별을 위한 값이며 인증수단이 아니다.
 */
public final class Identifiers {

    /** 업무 연도 표기에 쓰는 시간대. 저장은 전부 UTC({@code Instant})다. */
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");

    private Identifiers() {
    }

    public static String passportId() {
        return "PASS-" + uniqueSuffix();
    }

    public static String agentRunId() {
        return "RUN-" + uniqueSuffix();
    }

    public static String inputRef() {
        return "INPUT-" + uniqueSuffix();
    }

    /**
     * 연도는 업무 연도 표기이므로 UTC가 아니라 영업 시간대로 뽑는다.
     *
     * <p>UTC로 뽑으면 한국 시간 1월 1일 오전 9시까지 전년도가 찍힌다. 보안 문제는 아니지만
     * 감사 화면에 잘못된 해가 보인다.
     */
    public static String caseId(Instant issuedAt) {
        return "LOAN-" + issuedAt.atZone(BUSINESS_ZONE).getYear() + "-" + uniqueSuffix();
    }

    /**
     * 입력 원문의 해시. 원문 대신 이것만 저장한다 ({@code docs/06} §24).
     *
     * <p>같은 {@code (inputHash, modelVersion)}은 Prompt Risk를 재평가하지 않는 기준이 된다(§24.2).
     */
    public static String inputHash(String inputText) {
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(inputText.getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256은 모든 JDK가 제공한다. 여기 오면 런타임이 깨진 것이다.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /**
     * 128비트 전체를 쓴다.
     *
     * <p>앞 8자리(32비트)만 쓰면 한 종류 안에서 9,300개쯤에 1%, 77,000개쯤에 50% 확률로 충돌한다.
     * 이 값들은 기본키이고 재시도 경로가 없으므로, 충돌은 곧 실패한 요청이다.
     */
    private static String uniqueSuffix() {
        return UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
    }
}
