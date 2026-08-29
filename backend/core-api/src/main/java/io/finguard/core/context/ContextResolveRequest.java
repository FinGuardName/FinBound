package io.finguard.core.context;

import java.util.Set;
import java.util.UUID;

import io.finguard.core.domain.DataType;
import io.finguard.core.domain.Tool;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/** {@code POST /internal/v1/context/resolve} 요청. */
public record ContextResolveRequest(
        @NotNull UUID requestId,
        @NotBlank String verifiedAgentId,
        @NotBlank String agentRunId,
        @NotBlank String passportId,
        @NotBlank String targetConsumerId,
        @NotNull Tool requestedTool,
        @NotEmpty Set<@NotNull DataType> requestedData) {

    public ContextResolveRequest {
        requestedData = requestedData == null ? null : Set.copyOf(requestedData);
    }

    /** Tool이 실제로 읽는 Data를 요청 목록에서 빼 권한 검사를 우회할 수 없게 한다. */
    @AssertTrue(message = "requestedData must include the data required by requestedTool")
    public boolean isToolDataConsistent() {
        return requestedTool == null
                || requestedData == null
                || requestedData.contains(requestedTool.requiredData());
    }
}
