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
        properties = "finguard.internal.credential=test-internal-credential")
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

        // 501 = 필터를 통과해 컨트롤러까지 갔다는 뜻. 구현은 이슈 #21.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_IMPLEMENTED);
    }

    @Test
    void doesNotGuardPublicApiPaths() {
        var headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);

        ResponseEntity<String> response =
                restTemplate.exchange(
                        URI.create(base() + "/api/v1/agent-runs"),
                        org.springframework.http.HttpMethod.POST,
                        new org.springframework.http.HttpEntity<>("{}", headers),
                        String.class);

        // 빈 본문이라 검증에서 400이 난다. 중요한 것은 401이 아니라는 점 — 필터가 걸리지 않았다는 뜻이다.
        assertThat(response.getStatusCode())
                .as("/api/v1/** 는 사내 화면 경로라 서비스 간 자격 증명을 요구하지 않는다")
                .isEqualTo(HttpStatus.BAD_REQUEST);
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
