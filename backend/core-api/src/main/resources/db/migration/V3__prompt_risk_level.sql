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
