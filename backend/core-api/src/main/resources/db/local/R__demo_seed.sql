-- 데모 시드. local 프로파일에서만 로드된다 (application-local.yml).
--
-- 재현하려는 장면은 하나다.
--
--   EMP-101 은 CUST-9999 를 조회할 권한이 있다
--   그런데 지금 맡은 업무(LOAN-2026-001)의 대상은 CUST-1001 이다
--   → employeeAuthority = OK, customerScope = VIOLATION
--
-- 그래서 CUST-9999 는 반드시 실재해야 한다. 없는 고객이라 막히는 것과
-- 권한 범위 밖이라 막히는 것은 전혀 다른 이야기다.
--
-- TaskPassport 는 여기 넣지 않는다. Effective Permission 계산기가 실제로 발급해야 한다
-- (이슈 #19). 시드로 넣으면 계산기가 망가져도 데모가 성공해서 아무것도 증명하지 못한다.
--
-- Repeatable migration 이므로 내용이 바뀔 때마다 다시 실행된다. 아래의 고정 ID는 이 스크립트가
-- 소유한다. Scalar 행은 UPSERT하고 컬렉션은 지운 뒤 다시 넣어, 영속 Compose 볼륨에서도 파일에
-- 선언된 상태로 수렴시킨다. version은 충돌 시 증가시켜 기존 TaskPassport를 STALE로 만든다.

-- ---------------------------------------------------------------- 식별자

insert into employees (employee_id, created_at)
values ('EMP-101', timestamptz '2026-08-17 09:00:00+09')
on conflict (employee_id) do update
    set created_at = excluded.created_at;

insert into consumers (consumer_id, created_at)
values ('CUST-1001', timestamptz '2026-08-17 09:00:00+09'),
       ('CUST-9999', timestamptz '2026-08-17 09:00:00+09'),  -- 데모의 반대편. 실재해야 한다.
       ('CUST-1002', timestamptz '2026-08-17 09:00:00+09'),  -- Tool/Data 공격 Fixture
       ('CUST-1003', timestamptz '2026-08-17 09:00:00+09')   -- Mandate 공격 Fixture
on conflict (consumer_id) do update
    set created_at = excluded.created_at;

-- ---------------------------------------------------------------- 업무 표준

insert into permission_templates (template_id, task_type, default_duration_minutes, status, version)
values ('LOAN_REVIEW_STANDARD', 'LOAN_REVIEW', 60, 'ACTIVE', 1)
on conflict (template_id) do update
    set task_type = excluded.task_type,
        default_duration_minutes = excluded.default_duration_minutes,
        status = excluded.status,
        version = permission_templates.version + 1;

delete
from permission_template_allowed_tools
where template_id = 'LOAN_REVIEW_STANDARD';

insert into permission_template_allowed_tools (template_id, tool)
values ('LOAN_REVIEW_STANDARD', 'CREDIT_SCORE_READ'),
       ('LOAN_REVIEW_STANDARD', 'INCOME_READ'),
       ('LOAN_REVIEW_STANDARD', 'DEBT_READ')
on conflict (template_id, tool) do nothing;

delete
from permission_template_allowed_data
where template_id = 'LOAN_REVIEW_STANDARD';

insert into permission_template_allowed_data (template_id, data_type)
values ('LOAN_REVIEW_STANDARD', 'CREDIT_SCORE'),
       ('LOAN_REVIEW_STANDARD', 'INCOME'),
       ('LOAN_REVIEW_STANDARD', 'DEBT')
on conflict (template_id, data_type) do nothing;

-- ---------------------------------------------------------------- 직원 권한 (넓다)

-- allowed_customer_scope = ALL 이 데모의 전제다. EMP-101 개인은 모든 고객을 볼 수 있다.
insert into employee_authorities (employee_id, status, allowed_customer_scope, version)
values ('EMP-101', 'ACTIVE', 'ALL', 1)
on conflict (employee_id) do update
    set status = excluded.status,
        allowed_customer_scope = excluded.allowed_customer_scope,
        version = employee_authorities.version + 1;

delete
from employee_authority_allowed_tools
where employee_id = 'EMP-101';

insert into employee_authority_allowed_tools (employee_id, tool)
values ('EMP-101', 'CREDIT_SCORE_READ'),
       ('EMP-101', 'INCOME_READ'),
       ('EMP-101', 'DEBT_READ')
on conflict (employee_id, tool) do nothing;

delete
from employee_authority_allowed_data
where employee_id = 'EMP-101';

insert into employee_authority_allowed_data (employee_id, data_type)
values ('EMP-101', 'CREDIT_SCORE'),
       ('EMP-101', 'INCOME'),
       ('EMP-101', 'DEBT')
on conflict (employee_id, data_type) do nothing;

-- ---------------------------------------------------------------- 소비자 동의

-- CUST-1001 만 동의를 준다. CUST-9999 에는 mandate 가 없다.
insert into consumer_mandates (consumer_id, purpose, status, version)
values ('CUST-1001', 'LOAN_REVIEW', 'ACTIVE', 1)
on conflict (consumer_id, purpose) do update
    set status = excluded.status,
        version = consumer_mandates.version + 1;

delete
from consumer_mandate_allowed_data
where mandate_id in (
    select mandate_id
    from consumer_mandates
    where consumer_id = 'CUST-1001'
      and purpose = 'LOAN_REVIEW'
);

insert into consumer_mandate_allowed_data (mandate_id, data_type)
select m.mandate_id, d.data_type
from consumer_mandates m
         cross join (values ('CREDIT_SCORE'), ('INCOME'), ('DEBT')) as d(data_type)
where m.consumer_id = 'CUST-1001'
  and m.purpose = 'LOAN_REVIEW'
on conflict (mandate_id, data_type) do nothing;

-- ---------------------------------------------------------------- 공격 Scenario Fixture

-- docs/04-api-contract.md §3.1의 공격 Scenario가 실제로 차단되려면 발급되는 Passport가 좁아야 한다.
-- Passport = Employee Authority ∩ Permission Template ∩ Consumer Mandate 인데
-- (EffectivePermissionCalculator) 셋 중 실행 시점에 고를 수 있는 것은 Mandate 뿐이다 —
-- Authority는 운영 자격증명이 직원 하나에 묶여 있고 Template은 taskType으로 결정된다.
-- 그래서 좁은 Mandate를 가진 고객을 따로 둔다.
--
-- Data를 좁히면 그 Data를 요구하는 Tool도 함께 떨어진다(계산기의 removeIf). 그래서 Mandate 축소
-- 하나로 toolScope·dataScope·mandate 가 함께 위반된다. 의도한 동작이다 — 위반 하나만 나게 하는
-- 격리는 Authority/Template을 고를 수 없는 이상 불가능하다.

insert into consumer_mandates (consumer_id, purpose, status, version)
values ('CUST-1002', 'LOAN_REVIEW', 'ACTIVE', 1),
       ('CUST-1003', 'LOAN_REVIEW', 'ACTIVE', 1)
on conflict (consumer_id, purpose) do update
    set status = excluded.status,
        version = consumer_mandates.version + 1;

delete
from consumer_mandate_allowed_data
where mandate_id in (
    select mandate_id
    from consumer_mandates
    where consumer_id in ('CUST-1002', 'CUST-1003')
      and purpose = 'LOAN_REVIEW'
);

-- CUST-1002: INCOME 없음 → Passport tools {CREDIT_SCORE_READ, DEBT_READ}
insert into consumer_mandate_allowed_data (mandate_id, data_type)
select m.mandate_id, d.data_type
from consumer_mandates m
         cross join (values ('CREDIT_SCORE'), ('DEBT')) as d(data_type)
where m.consumer_id = 'CUST-1002'
  and m.purpose = 'LOAN_REVIEW'
on conflict (mandate_id, data_type) do nothing;

-- CUST-1003: DEBT 없음 → Passport tools {CREDIT_SCORE_READ, INCOME_READ}
insert into consumer_mandate_allowed_data (mandate_id, data_type)
select m.mandate_id, d.data_type
from consumer_mandates m
         cross join (values ('CREDIT_SCORE'), ('INCOME')) as d(data_type)
where m.consumer_id = 'CUST-1003'
  and m.purpose = 'LOAN_REVIEW'
on conflict (mandate_id, data_type) do nothing;

-- ---------------------------------------------------------------- 현재 업무

-- expires_at 을 먼 미래로 둔다. docs/01-feature-spec.md F04 예시의 2026-08-17 15:00 를 그대로 쓰면
-- 시드를 넣는 순간 이미 만료된 Case 가 되어 caseStatus = VIOLATION 이 되고, 정작 보여주려던
-- customerScope = VIOLATION 데모가 엉뚱한 이유로 실패한다.
insert into financial_cases (case_id, employee_id, consumer_id, task_type, template_id,
                             status, issued_at, expires_at, version)
values ('LOAN-2026-001', 'EMP-101', 'CUST-1001', 'LOAN_REVIEW', 'LOAN_REVIEW_STANDARD',
        'ACTIVE', timestamptz '2026-08-17 14:00:00+09', timestamptz '2030-12-31 23:59:59+09', 1)
on conflict (case_id) do update
    set employee_id = excluded.employee_id,
        consumer_id = excluded.consumer_id,
        task_type = excluded.task_type,
        template_id = excluded.template_id,
        status = excluded.status,
        issued_at = excluded.issued_at,
        expires_at = excluded.expires_at,
        version = financial_cases.version + 1;
