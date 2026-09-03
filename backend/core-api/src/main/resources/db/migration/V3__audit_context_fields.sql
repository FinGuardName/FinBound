-- contracts/audit/audit-event.schema.json은 AuditEvent에 29개 항목을 정의하는데
-- V1·V2에는 그중 9개를 담을 칸이 없었다. 칸이 없으니 저장된 모든 감사 기록이
-- "무엇을 보고 그렇게 판단했는지"를 빠뜨린 채 남아 있었다.
--
-- V1·V2는 고치지 않는다 — 이미 적용한 로컬 DB의 Flyway checksum이 깨진다(V2와 같은 이유).
--
-- 신규 컬럼은 전부 nullable이다. 이미 쌓인 행, 아직 Resolver를 거치지 않은 PROCESSING 행,
-- 평가 전에 실패한 행이 값을 갖지 못하는 것은 정당한 상태다.
--
-- employees·task_passports로 FK를 걸지 않는다. baseline이 의도적으로 원천 FK를 피했다 —
-- 원천 행이 지워질 때 감사 기록이 함께 무너지면 감사 기록이 아니다.

alter table audit_events
    add column employee_id                   varchar(64),
    add column passport_id                   varchar(64),

    -- Scope 판정 9개. 스키마의 $defs.scopeStatus가 멤버 9개를 required + additionalProperties:false로
    -- 고정하므로 JSON 한 칸이 아니라 개별 컬럼으로 둔다. 키 오타가 런타임까지 살아남지 않는다.
    add column scope_employee_authority      varchar(16) check (scope_employee_authority in ('OK', 'VIOLATION')),
    add column scope_permission_template     varchar(16) check (scope_permission_template in ('OK', 'VIOLATION')),
    add column scope_case_status             varchar(16) check (scope_case_status in ('OK', 'VIOLATION')),
    add column scope_mandate                 varchar(16) check (scope_mandate in ('OK', 'VIOLATION')),
    add column scope_passport_status         varchar(16) check (scope_passport_status in ('OK', 'VIOLATION')),
    add column scope_agent_binding           varchar(16) check (scope_agent_binding in ('OK', 'VIOLATION')),
    add column scope_customer_scope          varchar(16) check (scope_customer_scope in ('OK', 'VIOLATION')),
    add column scope_tool_scope              varchar(16) check (scope_tool_scope in ('OK', 'VIOLATION')),
    add column scope_data_scope              varchar(16) check (scope_data_scope in ('OK', 'VIOLATION')),

    add column prompt_risk_evaluation_status varchar(32) check (prompt_risk_evaluation_status in ('EVALUATED', 'NOT_EVALUATED')),
    add column prompt_model_version          varchar(64),

    -- 아래 셋은 AI가 Gateway에 준 값이라 Core가 알 길이 없다. 칸만 만들어 두고 채우는 경로는
    -- docs/04-api-contract.md §11 Outcome 본문 확장이 정해진 뒤에 붙인다 — 계약 파일이라 팀 합의 대상이다.
    add column behavior_risk_level           varchar(16) check (behavior_risk_level in ('LOW', 'ALERT', 'CRITICAL')),
    add column behavior_feature_version      varchar(64),
    add column behavior_model_version        varchar(64),

    -- 한 감사 기록에 쓰는 주체가 선저장·증거 기록·최종 결과로 늘어난다. 낙관적 락이 없으면
    -- 늦게 도착한 쓰기가 다른 쪽이 방금 채운 칸을 옛 값으로 되돌린다.
    add column version                       bigint not null default 0,

    -- Scope는 9개가 전부 차거나 전부 비거나 둘 중 하나다. 일부만 채우면 빠진 자리를 OK로 읽는
    -- 사람이 생기고, 그러면 위반이 조용히 사라진다.
    add constraint ck_audit_events_scope_status_all_or_none
        check (
            (scope_employee_authority is null
                and scope_permission_template is null
                and scope_case_status is null
                and scope_mandate is null
                and scope_passport_status is null
                and scope_agent_binding is null
                and scope_customer_scope is null
                and scope_tool_scope is null
                and scope_data_scope is null)
            or (scope_employee_authority is not null
                and scope_permission_template is not null
                and scope_case_status is not null
                and scope_mandate is not null
                and scope_passport_status is not null
                and scope_agent_binding is not null
                and scope_customer_scope is not null
                and scope_tool_scope is not null
                and scope_data_scope is not null)),

    -- 스키마의 riskScore는 0~1이다. V1은 이 범위를 애플리케이션 검증에만 맡겨 뒀다.
    add constraint ck_audit_events_prompt_risk_range
        check (prompt_risk is null or (prompt_risk >= 0 and prompt_risk <= 1)),
    add constraint ck_audit_events_behavior_risk_range
        check (behavior_risk is null or (behavior_risk >= 0 and behavior_risk <= 1));

-- 시도한 Data 종류. audit_event_reason_codes와 같은 모양이다.
-- 스키마의 minItems:1은 여기서 강제할 수 없어(행이 아예 없는 것과 구분 불가) 애플리케이션이 맡는다.
create table audit_event_requested_data (
    audit_event_id varchar(64) not null,
    data_type      varchar(64) not null check (data_type in ('CREDIT_SCORE', 'INCOME', 'DEBT')),
    primary key (audit_event_id, data_type),
    constraint fk_requested_data_audit_event foreign key (audit_event_id) references audit_events
);
