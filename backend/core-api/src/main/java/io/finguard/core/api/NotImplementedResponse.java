package io.finguard.core.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

/**
 * 계약에는 있으나 아직 구현되지 않은 엔드포인트의 응답.
 *
 * <p>고정된 성공 응답 대신 501을 낸다. 붙이는 쪽이 404(경로를 잘못 붙임)와 501(아직 안 만듦)을
 * 구분할 수 있어야 하고, 가짜 성공 응답 때문에 통합이 끝났다고 착각하는 일이 없어야 한다.
 *
 * <p>응답에 {@code reasonCode}를 넣지 않는다. 그 필드는 계약이 정의한 어휘
 * (docs/06-common-conventions.md §20)만 담아야 하는데 "아직 구현하지 않았다"에 해당하는 코드가
 * 거기에 없다. 계약에 없는 값을 계약 필드에 넣으면 소비자가 그걸 실재하는 코드로 오해한다.
 * 501과 title만으로 충분히 구분된다.
 */
public final class NotImplementedResponse {

    private NotImplementedResponse() {
    }

    public static ResponseEntity<ProblemDetail> forEndpoint(String endpoint) {
        ProblemDetail body = ProblemDetail.forStatus(HttpStatus.NOT_IMPLEMENTED);
        body.setTitle("Not implemented");
        body.setDetail(endpoint + " is part of the frozen contract but is not implemented yet.");
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(body);
    }
}
