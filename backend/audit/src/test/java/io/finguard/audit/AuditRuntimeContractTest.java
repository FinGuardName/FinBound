package io.finguard.audit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;

class AuditRuntimeContractTest {

    private static final Path CONTRACT_DIRECTORY = Path.of(
        System.getProperty("finguard.repository.root"),
        "contracts",
        "audit"
    );

    @ParameterizedTest(name = "{0}")
    @MethodSource("validContracts")
    void acceptsDocumentThatSatisfiesContract(String scenario, String schemaFile, String fixtureFile)
        throws IOException {
        List<Error> errors = validate(schemaFile, fixtureFile);

        assertTrue(errors.isEmpty(), () -> scenario + " should be valid, but was: " + errors);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidContracts")
    void rejectsDocumentThatViolatesContract(String scenario, String schemaFile, String fixtureFile)
        throws IOException {
        List<Error> errors = validate(schemaFile, fixtureFile);

        assertFalse(errors.isEmpty(), () -> scenario + " should be rejected");
    }

    private static Stream<Arguments> validContracts() {
        return Stream.of(
            Arguments.of(
                "ToolCallAttempt에는 현재 시점의 값만 기록",
                "tool-call-attempt.schema.json",
                "tool-call-attempt.valid.json"
            ),
            Arguments.of(
                "ALLOW 완료 시 Downstream과 응답 반환",
                "execution-outcome.schema.json",
                "execution-outcome.allow.valid.json"
            ),
            Arguments.of(
                "BLOCK 완료 시 Downstream 미도달",
                "execution-outcome.schema.json",
                "execution-outcome.block.valid.json"
            ),
            Arguments.of(
                "시스템 장애는 Decision이 아닌 ERROR Outcome",
                "execution-outcome.schema.json",
                "execution-outcome.error.valid.json"
            ),
            Arguments.of(
                "인증 성공 직후 PROCESSING Business Audit 생성",
                "audit-event.schema.json",
                "audit-event.processing.valid.json"
            ),
            Arguments.of(
                "ALLOW Business Audit 완료",
                "audit-event.schema.json",
                "audit-event.allow.valid.json"
            ),
            Arguments.of(
                "BLOCK Business Audit도 COMPLETED 상태",
                "audit-event.schema.json",
                "audit-event.block.valid.json"
            ),
            Arguments.of(
                "Core 장애는 ERROR Business Audit으로 완료",
                "audit-event.schema.json",
                "audit-event.error.valid.json"
            ),
            Arguments.of(
                "인증 실패는 최소 SecurityAuthEvent로 분리",
                "security-auth-event.schema.json",
                "security-auth-event.valid.json"
            )
        );
    }

    private static Stream<Arguments> invalidContracts() {
        return Stream.of(
            Arguments.of(
                "ToolCallAttempt의 미래 실행값 차단",
                "tool-call-attempt.schema.json",
                "tool-call-attempt.future-values.invalid.json"
            ),
            Arguments.of(
                "Decision ERROR 차단",
                "execution-outcome.schema.json",
                "execution-outcome.invalid-decision.json"
            ),
            Arguments.of(
                "BLOCK의 Downstream 도달 차단",
                "execution-outcome.schema.json",
                "execution-outcome.block-reached-downstream.invalid.json"
            ),
            Arguments.of(
                "Business Audit의 민감 원문 차단",
                "audit-event.schema.json",
                "audit-event.sensitive-data.invalid.json"
            ),
            Arguments.of(
                "BLOCK Business Audit의 실행 측정값 차단",
                "audit-event.schema.json",
                "audit-event.block-execution-values.invalid.json"
            ),
            Arguments.of(
                "인증 실패 Event의 Business Audit 필드 차단",
                "security-auth-event.schema.json",
                "security-auth-event.business-audit-fields.invalid.json"
            ),
            Arguments.of(
                "인증 실패 Event의 민감 원문 차단",
                "security-auth-event.schema.json",
                "security-auth-event.sensitive-data.invalid.json"
            )
        );
    }

    private static List<Error> validate(String schemaFile, String fixtureFile) throws IOException {
        SchemaRegistry registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);
        String schemaDocument = Files.readString(CONTRACT_DIRECTORY.resolve(schemaFile));
        String fixtureDocument = Files.readString(
            CONTRACT_DIRECTORY.resolve("fixtures").resolve(fixtureFile)
        );
        Schema schema = registry.getSchema(schemaDocument);
        schema.initializeValidators();

        return schema.validate(
            fixtureDocument,
            InputFormat.JSON,
            executionContext -> executionContext.executionConfig(
                executionConfig -> executionConfig.formatAssertionsEnabled(true)
            )
        );
    }
}
