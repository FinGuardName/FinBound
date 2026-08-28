package io.finguard.core.agentrun;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class IdentifiersTest {

    @Test
    void identifiersUseTheFullUuidWidth() {
        // 앞 8자리(32비트)만 쓰면 한 종류 안에서 9,300개쯤에 1% 확률로 충돌한다.
        // 이 값들은 기본키이고 재시도 경로가 없어서, 충돌은 곧 실패한 요청이다.
        String passportId = Identifiers.passportId();

        assertThat(passportId).startsWith("PASS-");
        assertThat(passportId.substring("PASS-".length())).hasSize(32).matches("[0-9A-F]{32}");
    }

    @Test
    void identifiersDoNotRepeat() {
        Set<String> generated = new HashSet<>();
        IntStream.range(0, 5_000).forEach(i -> generated.add(Identifiers.agentRunId()));

        assertThat(generated).hasSize(5_000);
    }

    @Test
    void caseIdUsesTheBusinessYearNotUtc() {
        // 한국 시간 2027-01-01 00:30. UTC 로는 아직 2026년이다.
        Instant justAfterKoreanNewYear = Instant.parse("2026-12-31T15:30:00Z");

        assertThat(Identifiers.caseId(justAfterKoreanNewYear)).startsWith("LOAN-2027-");
    }

    @Test
    void inputHashIsDeterministicAndPrefixed() {
        // 같은 (inputHash, modelVersion) 은 Prompt Risk 를 재평가하지 않는 기준이다 — docs/06 §24.2.
        // 해시가 매번 달라지면 그 재사용이 성립하지 않는다.
        String first = Identifiers.inputHash("CUST-1001의 대출심사를 진행해줘.");
        String second = Identifiers.inputHash("CUST-1001의 대출심사를 진행해줘.");

        assertThat(first).isEqualTo(second).startsWith("sha256:");
        assertThat(first.substring("sha256:".length())).hasSize(64);
        assertThat(Identifiers.inputHash("다른 입력")).isNotEqualTo(first);
    }
}
