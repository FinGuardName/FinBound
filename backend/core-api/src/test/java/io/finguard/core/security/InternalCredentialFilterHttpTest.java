package io.finguard.core.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 실제 서블릿 컨테이너를 지나가는 인증 테스트.
 *
 * <p>{@code MockHttpServletRequest}로 필터를 직접 호출하는 테스트는 컨테이너의 경로 매핑을 거치지
 * 않는다. 그 결과 요청 경로를 컨테이너와 필터가 서로 다르게 해석하는 종류의 우회를 잡지 못한다.
 * 이 테스트는 Tomcat과 DispatcherServlet을 전부 통과시켜 그 간극을 검증한다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "finguard.internal.credential=test-internal-credential",
            "finguard.api.viewer-credential=test-viewer-credential",
            "finguard.api.operator-credential=test-operator-credential",
            "finguard.api.operator-employee-id=EMP-101",
        })
@Testcontainers
class InternalCredentialFilterHttpTest {

    @Container
    @ServiceConnection
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.10-alpine");

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    /**
     * 컨테이너가 디코딩·정규화한 뒤 컨트롤러로 보내는 경로는 모두 인증을 거쳐야 한다.
     *
     * <p>아래 변형들은 전부 {@code /internal/v1/audits} 로 라우팅된다. 하나라도 401이 아니면
     * 자격 증명 없이 Internal API에 도달할 수 있다는 뜻이다.
     */
    @ParameterizedTest(name = "{0} -> 401")
    @ValueSource(
            strings = {
                "/internal/v1/audits",
                "/%69nternal/v1/audits", // %69 == 'i'
                "/internal;x=1/v1/audits", // 경로(matrix) 파라미터
                "/internal/v1/audits;x=1",
                "/internal//v1/audits",
                "/internal/v1/./audits",
            })
    void rejectsEveryRoutableSpellingOfAnInternalPath(String rawPath) {
        ResponseEntity<String> response =
                restTemplate.postForEntity(URI.create(base() + rawPath), "{}", String.class);

        assertThat(response.getStatusCode())
                .as("자격 증명 없이 %s 가 컨트롤러에 도달하면 안 된다", rawPath)
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void allowsInternalPathWithValidCredential() {
        var headers = new org.springframework.http.HttpHeaders();
        headers.add(InternalCredentialFilter.CREDENTIAL_HEADER, "test-internal-credential");

        ResponseEntity<String> response =
                restTemplate.exchange(
                        URI.create(base() + "/internal/v1/audits"),
                        org.springframework.http.HttpMethod.POST,
                        new org.springframework.http.HttpEntity<>("{}", headers),
                        String.class);

        // 400 = 필터를 통과해 구현된 컨트롤러의 필수 헤더/본문 검증까지 갔다는 뜻.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    /**
     * {@code /api/v1/**}에 Internal Credential을 들이밀어도 통하지 않는다.
     *
     * <p>두 경로는 서로 다른 자격 증명 체계를 쓴다({@code docs/04-api-contract.md} §2).
     * 하나로 다른 하나를 열 수 있으면 §2가 나눠 둔 신뢰 경계가 무너진다.
     */
    @Test
    void doesNotLetTheInternalCredentialOpenPublicApiPaths() {
        var headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        headers.set(InternalCredentialFilter.CREDENTIAL_HEADER, "test-internal-credential");

        ResponseEntity<String> response =
                restTemplate.exchange(
                        URI.create(base() + "/api/v1/agent-runs"),
                        org.springframework.http.HttpMethod.POST,
                        new org.springframework.http.HttpEntity<>("{}", headers),
                        String.class);

        assertThat(response.getStatusCode())
                .as("/api/v1/** 는 Bearer Credential을 요구하며 Internal Credential로는 열리지 않는다")
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void doesNotGuardActuatorHealth() {
        ResponseEntity<String> response =
                restTemplate.getForEntity(URI.create(base() + "/actuator/health"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private String base() {
        return "http://localhost:" + port;
    }
}
