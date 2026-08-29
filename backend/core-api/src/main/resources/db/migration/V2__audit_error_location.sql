-- contracts/audit/execution-outcome.schema.json은 systemOutcome=ERROR에 errorLocation을 요구한다.
-- V1에는 이 컬럼이 없어 모든 ERROR 기록이 스키마를 위반한 상태로 저장됐다.
-- V1을 고치지 않고 새 버전을 추가한다 — 이미 V1을 적용한 로컬 DB의 Flyway checksum을 깨지 않기 위해서다.
-- 값 형식은 스키마의 upperSnakeCase를 그대로 강제한다. ERROR일 때만 값이 있으므로 null은 허용한다.
-- "ERROR면 반드시 있어야 한다"는 조건부 필수는 여기서 걸 수 없어 애플리케이션 검증이 맡는다.
alter table audit_events
    add column error_location varchar(64),
    add constraint ck_audit_events_error_location
        check (error_location is null or error_location ~ '^[A-Z][A-Z0-9_]*$');
