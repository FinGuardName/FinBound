package io.finguard.core.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

class NotImplementedResponseTest {

    @Test
    void reportsNotImplemented() {
        ResponseEntity<ProblemDetail> response =
                NotImplementedResponse.forEndpoint("POST /internal/v1/context/resolve");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_IMPLEMENTED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTitle()).isEqualTo("Not implemented");
    }

    @Test
    void doesNotPutANonContractValueIntoReasonCode() {
        // reasonCode는 docs/06-common-conventions.md §20의 어휘만 담는다.
        // "아직 구현 안 됨"에 해당하는 코드가 거기 없으므로 이 필드를 아예 쓰지 않는다.
        ResponseEntity<ProblemDetail> response =
                NotImplementedResponse.forEndpoint("POST /internal/v1/audits");

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getProperties()).isNullOrEmpty();
    }

    @Test
    void namesTheEndpointSoCallersCanTellWhichOneIsMissing() {
        ResponseEntity<ProblemDetail> response =
                NotImplementedResponse.forEndpoint("POST /internal/v1/audits");

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDetail()).contains("POST /internal/v1/audits");
    }
}
