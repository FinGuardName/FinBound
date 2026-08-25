export const bankWorkContextFixture = {
  employee: {
    id: 'EMP-101',
    name: '김서윤 대리',
    branch: '강남기업금융센터',
    authorityLabel: '담당 고객 2명',
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
    applicationAmountLabel: '3,000만원',
    receivedAtLabel: '2026.08.25 09:40',
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
  { id: 1, label: '업무 확인', title: '현재 대출 신청 건 확인', description: '김○○ 고객의 신규 대출 심사 업무에 연결했습니다.', state: 'ALLOW' },
  { id: 2, label: '자료 확인', title: 'AI가 필요한 심사자료 확인 시작', description: '신용정보·소득·부채 자료 확인을 준비했습니다.', state: 'ALLOW' },
]

export const agentExecutionFixtures = {
  IN_SCOPE: {
    requestId: 'REQ-001',
    targetConsumerId: 'CUST-1001',
    tool: 'CREDIT_SCORE_READ',
    decision: 'ALLOW',
    title: '심사자료 확인이 완료되었습니다',
    message: '현재 신청 고객의 동의와 업무 범위 안에서 필요한 자료만 확인했습니다.',
    reasonCodes: [],
    downstreamReached: true,
    scopeStatus: { customerScope: 'OK', toolScope: 'OK', dataScope: 'OK', mandate: 'OK' },
    steps: [
      ...commonSteps,
      { id: 3, label: '보호 확인', title: '고객 동의와 업무 범위 확인 완료', description: '현재 신청 건에 필요한 자료만 포함되어 있습니다.', state: 'ALLOW' },
      { id: 4, label: '확인 완료', title: '심사자료를 현재 신청 건에 연결', description: '확인된 결과를 대출 심사 업무에 안전하게 전달했습니다.', state: 'ALLOW' },
    ],
  },
  OUT_OF_SCOPE: {
    requestId: 'REQ-002',
    targetConsumerId: 'CUST-9999',
    tool: 'CREDIT_SCORE_READ',
    decision: 'BLOCK',
    title: '현재 신청 건과 관련 없는 자료는 확인하지 않았습니다',
    message: '다른 고객 자료가 포함된 요청이어서 안전하게 중단했습니다. 현재 신청 고객 자료만 다시 요청해 주세요.',
    reasonCodes: ['CASE_SCOPE_VIOLATION'],
    downstreamReached: false,
    scopeStatus: { customerScope: 'VIOLATION', toolScope: 'OK', dataScope: 'OK', mandate: 'OK' },
    steps: [
      ...commonSteps,
      { id: 3, label: '보호 확인', title: '다른 고객 자료 포함 확인', description: '현재 신청 고객이 아닌 자료가 포함되어 보호 설정이 작동했습니다.', state: 'BLOCK' },
      { id: 4, label: '안전 중단', title: '금융시스템 조회 전 요청 중단', description: '다른 고객의 자료는 조회하지 않았습니다.', state: 'BLOCK' },
    ],
  },
}

export const auditEventsFixture = [
  { auditEventId: 'AUD-003', requestedAt: '2026-08-22T17:48:22+09:00', agentId: 'LOAN-AGENT-01', employeeId: 'EMP-101', caseId: 'LOAN-2026-001', targetConsumerId: 'CUST-1001', tool: 'INCOME_READ', decision: 'ERROR', severity: 'HIGH', reasonCodes: ['DOWNSTREAM_TIMEOUT'], riskFlagged: true, promptRisk: 0.04, behaviorRisk: 0.18, downstreamReached: true, responseReleased: false, scopeStatus: { customerScope: 'OK', toolScope: 'OK', dataScope: 'OK', mandate: 'OK' } },
  { auditEventId: 'AUD-002', requestedAt: '2026-08-22T17:44:06+09:00', agentId: 'LOAN-AGENT-01', employeeId: 'EMP-101', caseId: 'LOAN-2026-001', targetConsumerId: 'CUST-9999', tool: 'CREDIT_SCORE_READ', decision: 'BLOCK', severity: 'HIGH', reasonCodes: ['CASE_SCOPE_VIOLATION'], riskFlagged: true, promptRisk: 0.12, behaviorRisk: 0.31, downstreamReached: false, responseReleased: false, scopeStatus: { customerScope: 'VIOLATION', toolScope: 'OK', dataScope: 'OK', mandate: 'OK' } },
  { auditEventId: 'AUD-001', requestedAt: '2026-08-22T17:39:41+09:00', agentId: 'LOAN-AGENT-01', employeeId: 'EMP-101', caseId: 'LOAN-2026-001', targetConsumerId: 'CUST-1001', tool: 'CREDIT_SCORE_READ', decision: 'ALLOW', severity: 'LOW', reasonCodes: [], riskFlagged: false, promptRisk: 0.05, behaviorRisk: 0.21, downstreamReached: true, responseReleased: true, scopeStatus: { customerScope: 'OK', toolScope: 'OK', dataScope: 'OK', mandate: 'OK' } },
]
