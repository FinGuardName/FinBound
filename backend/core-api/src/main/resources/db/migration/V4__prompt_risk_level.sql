alter table prompt_risk_snapshots add column risk_level varchar(16) default 'LOW';

update prompt_risk_snapshots
set risk_level = case when detected then 'CRITICAL' else 'LOW' end;

alter table prompt_risk_snapshots alter column risk_level set not null;
alter table prompt_risk_snapshots alter column risk_level drop default;
alter table prompt_risk_snapshots
    add constraint chk_prompt_risk_level check (risk_level in ('LOW', 'ALERT', 'CRITICAL'));
alter table prompt_risk_snapshots
    add constraint chk_prompt_risk_detection_consistency check (
        (detected and risk_level = 'CRITICAL')
        or (not detected and risk_level <> 'CRITICAL')
    );

-- V3가 추가한 감사 증거에도 같은 등급을 보존한다. PROCESSING 행은 아직 Resolver 증거가
-- 없으므로 nullable로 둔다. 과거 EVALUATED 행은 당시 등급을 저장하지 않아 복원할 수 없으므로
-- 값을 추측하지 않고, 의미가 확정적인 NOT_EVALUATED 행만 LOW로 채운다.
alter table audit_events add column prompt_risk_level varchar(16);

update audit_events
set prompt_risk_level = 'LOW'
where prompt_risk_evaluation_status = 'NOT_EVALUATED';

alter table audit_events
    add constraint chk_audit_prompt_risk_level check (
        prompt_risk_level is null or prompt_risk_level in ('LOW', 'ALERT', 'CRITICAL')
    );

-- OPA 판정 결과를 프론트에서 재계산하지 않고 그대로 보존한다. 과거 행과 정책 판정 전
-- 시스템 오류는 원래 값을 복원할 수 없으므로 nullable로 두고 추측해 채우지 않는다.
alter table audit_events add column severity varchar(16);
alter table audit_events add column risk_flagged boolean;
alter table audit_events
    add constraint chk_audit_severity check (
        severity is null or severity in ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')
    );
alter table audit_events
    add constraint chk_audit_policy_risk_pair check (
        (severity is null and risk_flagged is null)
        or (severity is not null and risk_flagged is not null)
    );

create index idx_audit_event_severity_requested_at
    on audit_events (severity, requested_at desc);
create index idx_audit_event_risk_flagged_requested_at
    on audit_events (risk_flagged, requested_at desc);
