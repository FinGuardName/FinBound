-- Public AgentRun 실행 조회는 전체 Dashboard 페이지를 훑지 않고 해당 실행의 감사행만 읽는다.
create index idx_audit_events_agent_run_requested_at
    on audit_events (agent_run_id, requested_at, audit_event_id);
