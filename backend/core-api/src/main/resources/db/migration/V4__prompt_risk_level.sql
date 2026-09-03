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
