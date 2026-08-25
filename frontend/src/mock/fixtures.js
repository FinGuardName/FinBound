export const bankWorkContextFixture = {
  employee: {
    id: 'EMP-101',
    name: '김서윤 대리',
    branch: '강남기업금융센터',
    authorityScope: 'CUST-1001 · CUST-9999',
  },
  case: {
    caseId: 'LOAN-2026-001',
    consumerId: 'CUST-1001',
    consumerLabel: '김○○ 고객',
    productName: '직장인 신용대출 신규 심사',
    purpose: '신규 대출 한도 산정',
    taskLabel: 'LOAN_REVIEW',
    statusLabel: '심사 진행 중',
    expiresAtLabel: '오늘 18:30 KST',
  },
  passport: {
    passportId: 'PASS-001',
    agentId: 'LOAN-AGENT-01',
    status: 'ACTIVE',
    expiresAtLabel: '18:30 KST',
    allowedTools: ['CREDIT_SCORE_READ', 'INCOME_READ', 'DEBT_READ'],
  },
}

const commonSteps = [
  { id: 1, label: 'BANK WORK', title: '대출 심사 보조 요청 접수', description: '은행원의 현재 업무 Context를 AgentRun에 연결했습니다.', state: 'ALLOW' },
  { id: 2, label: 'AGENT ACTION', title: '금융 Tool Call 실행 시도', description: 'LoanAgent가 고객 신용정보 조회를 요청했습니다.', state: 'ALLOW' },
]

export const agentExecutionFixtures = {
  IN_SCOPE: {
    requestId: 'REQ-001',
    targetConsumerId: 'CUST-1001',
    tool: 'CREDIT_SCORE_READ',
    decision: 'ALLOW',
    title: '허용된 업무를 안전하게 실행했습니다',
    message: '현재 Case의 고객과 허용 Tool 범위가 일치해 금융 API 호출을 승인했습니다.',
    reasonCodes: [],
    downstreamReached: true,
    scopeStatus: { customerScope: 'OK', toolScope: 'OK', dataScope: 'OK', mandate: 'OK' },
    steps: [
      ...commonSteps,
      { id: 3, label: 'FINGUARD CHECK', title: '현재 Case 권한 검증 완료', description: 'Customer, Tool, Data, Mandate Scope가 모두 유효합니다.', state: 'ALLOW' },
      { id: 4, label: 'DOWNSTREAM', title: '금융 API 호출 허용', description: '검증된 요청만 Mock Finance에 1회 전달했습니다.', state: 'ALLOW' },
    ],
  },
  OUT_OF_SCOPE: {
    requestId: 'REQ-002',
    targetConsumerId: 'CUST-9999',
    tool: 'CREDIT_SCORE_READ',
    decision: 'BLOCK',
    title: '권한 외 고객 조회를 차단했습니다',
    message: '직원은 조회할 수 있는 고객이지만, 현재 Agent가 맡은 Case의 고객이 아니므로 실행하지 않았습니다.',
    reasonCodes: ['CASE_SCOPE_VIOLATION'],
    downstreamReached: false,
    scopeStatus: { customerScope: 'VIOLATION', toolScope: 'OK', dataScope: 'OK', mandate: 'OK' },
    steps: [
      ...commonSteps,
      { id: 3, label: 'FINGUARD CHECK', title: '현재 Case 범위 위반 감지', description: '요청 고객 CUST-9999는 현재 Case 고객 CUST-1001과 다릅니다.', state: 'BLOCK' },
      { id: 4, label: 'DOWNSTREAM', title: '금융 API 호출 전 차단', description: '권한 외 요청은 Mock Finance에 전달되지 않았습니다.', state: 'BLOCK' },
    ],
  },
}

export const auditEventsFixture = [
  { auditEventId: 'AUD-003', requestedAt: '2026-08-22T17:48:22+09:00', agentId: 'LOAN-AGENT-01', employeeId: 'EMP-101', caseId: 'LOAN-2026-001', targetConsumerId: 'CUST-1001', tool: 'INCOME_READ', decision: 'ERROR', severity: 'HIGH', reasonCodes: ['DOWNSTREAM_TIMEOUT'], riskFlagged: true, promptRisk: 0.04, behaviorRisk: 0.18, downstreamReached: true, responseReleased: false, scopeStatus: { customerScope: 'OK', toolScope: 'OK', dataScope: 'OK', mandate: 'OK' } },
  { auditEventId: 'AUD-002', requestedAt: '2026-08-22T17:44:06+09:00', agentId: 'LOAN-AGENT-01', employeeId: 'EMP-101', caseId: 'LOAN-2026-001', targetConsumerId: 'CUST-9999', tool: 'CREDIT_SCORE_READ', decision: 'BLOCK', severity: 'HIGH', reasonCodes: ['CASE_SCOPE_VIOLATION'], riskFlagged: true, promptRisk: 0.12, behaviorRisk: 0.31, downstreamReached: false, responseReleased: false, scopeStatus: { customerScope: 'VIOLATION', toolScope: 'OK', dataScope: 'OK', mandate: 'OK' } },
  { auditEventId: 'AUD-001', requestedAt: '2026-08-22T17:39:41+09:00', agentId: 'LOAN-AGENT-01', employeeId: 'EMP-101', caseId: 'LOAN-2026-001', targetConsumerId: 'CUST-1001', tool: 'CREDIT_SCORE_READ', decision: 'ALLOW', severity: 'LOW', reasonCodes: [], riskFlagged: false, promptRisk: 0.05, behaviorRisk: 0.21, downstreamReached: true, responseReleased: true, scopeStatus: { customerScope: 'OK', toolScope: 'OK', dataScope: 'OK', mandate: 'OK' } },
]
