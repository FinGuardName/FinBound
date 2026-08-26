const employeeFixture = {
  id: 'EMP-101',
  name: '김서윤 대리',
  branch: '강남기업금융센터',
  authorityLabel: '담당 고객 6명',
  authorityScope: 'CUST-1001 · CUST-2001 · CUST-3001 외',
}

export const bankWorkCatalogFixture = [
  {
    id: 'NEW_LOAN',
    shortLabel: '신규 대출 심사',
    summary: '신규 신청 고객의 기본 심사자료를 확인합니다.',
    badge: '신규',
    employee: employeeFixture,
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
      agentRunId: 'RUN-001',
      agentId: 'LOAN-AGENT-01',
      expiresAtLabel: '18:30 KST',
      allowedTools: ['CREDIT_SCORE_READ', 'INCOME_READ', 'DEBT_READ'],
    },
    employeeRequest: {
      title: '현재 고객의 신규 대출 심사자료 확인',
      description: '신용정보·소득·부채 자료를 확인하고 현재 신청 건에 정리합니다.',
    },
  },
  {
    id: 'LIMIT_REVIEW',
    shortLabel: '대출 한도 재심사',
    summary: '한도 증액을 위해 변경된 상환능력을 다시 확인합니다.',
    badge: '재심사',
    employee: employeeFixture,
    case: {
      caseId: 'LOAN-2026-014',
      consumerId: 'CUST-2001',
      consumerLabel: '이○○ 고객',
      productName: '신용대출 한도 증액 재심사',
      purpose: '대출 한도 증액 검토',
      taskLabel: 'LOAN_REVIEW',
      statusLabel: '재심사 진행 중',
      applicationAmountLabel: '5,000만원',
      receivedAtLabel: '2026.08.25 10:15',
      expiresAtLabel: '오늘 17:50 KST',
    },
    passport: {
      passportId: 'PASS-014',
      agentRunId: 'RUN-014',
      agentId: 'LOAN-AGENT-01',
      expiresAtLabel: '17:50 KST',
      allowedTools: ['INCOME_READ', 'DEBT_READ'],
    },
    employeeRequest: {
      title: '현재 고객의 변경된 상환능력 재확인',
      description: '최신 소득·부채 자료를 확인하고 한도 재심사 건에 정리합니다.',
    },
  },
  {
    id: 'DOCUMENT_REVIEW',
    shortLabel: '심사서류 보완 확인',
    summary: '추가 제출된 소득·부채 서류의 확인 범위를 점검합니다.',
    badge: '보완',
    employee: employeeFixture,
    case: {
      caseId: 'LOAN-2026-027',
      consumerId: 'CUST-3001',
      consumerLabel: '박○○ 고객',
      productName: '대출 심사서류 보완 확인',
      purpose: '추가 제출자료 확인',
      taskLabel: 'LOAN_REVIEW',
      statusLabel: '서류 보완 중',
      applicationAmountLabel: '8,000만원',
      receivedAtLabel: '2026.08.25 11:05',
      expiresAtLabel: '오늘 16:40 KST',
    },
    passport: {
      passportId: 'PASS-027',
      agentRunId: 'RUN-027',
      agentId: 'LOAN-AGENT-01',
      expiresAtLabel: '16:40 KST',
      allowedTools: ['INCOME_READ', 'DEBT_READ'],
    },
    employeeRequest: {
      title: '현재 고객이 제출한 보완자료 확인',
      description: '추가 제출된 소득·부채 자료를 확인하고 심사서류에 반영합니다.',
    },
  },
]

const okScope = { customerScope: 'OK', toolScope: 'OK', dataScope: 'OK', mandate: 'OK' }

function createAttempt({ requestId, label, description, targetConsumerId, tool, decision, reasonCodes = [], scopeStatus = okScope }) {
  return {
    requestId,
    label,
    description,
    targetConsumerId,
    tool,
    decision,
    reasonCodes,
    downstreamReached: decision === 'ALLOW',
    scopeStatus,
  }
}

export const agentExecutionFixtures = {
  NEW_LOAN: {
    status: 'COMPLETED',
    title: '신규 대출 심사자료 확인이 완료되었습니다',
    message: '현재 업무에 필요한 자료는 정상 확인했고, AI가 시도한 다른 고객 조회 1건은 자료 조회 전에 차단했습니다.',
    resultHeading: '신규 심사자료가 준비되었습니다',
    resultItems: ['신용정보 확인 완료', '소득·부채 자료 확인 완료', '다른 고객 조회 1건 차단', '현재 신청 건에 결과 연결 완료'],
    nextAction: '확인된 자료를 바탕으로 심사 의견을 작성해 주세요.',
    attempts: [
      createAttempt({ requestId: 'REQ-001-A', label: '현재 고객 신용정보 확인', description: '신규 대출 심사에 필요한 신용정보를 확인했습니다.', targetConsumerId: 'CUST-1001', tool: 'CREDIT_SCORE_READ', decision: 'ALLOW' }),
      createAttempt({ requestId: 'REQ-001-B', label: '현재 고객 소득자료 확인', description: '상환능력 판단에 필요한 소득자료를 확인했습니다.', targetConsumerId: 'CUST-1001', tool: 'INCOME_READ', decision: 'ALLOW' }),
      createAttempt({ requestId: 'REQ-001-C', label: '현재 고객 부채자료 확인', description: '총부채 확인에 필요한 자료를 확인했습니다.', targetConsumerId: 'CUST-1001', tool: 'DEBT_READ', decision: 'ALLOW' }),
      createAttempt({ requestId: 'REQ-002', label: '유사 고객 신용정보 추가 조회', description: '현재 신청 건과 무관한 고객 자료이므로 금융시스템에 전달하지 않았습니다.', targetConsumerId: 'CUST-9999', tool: 'CREDIT_SCORE_READ', decision: 'BLOCK', reasonCodes: ['CASE_SCOPE_VIOLATION'], scopeStatus: { ...okScope, customerScope: 'VIOLATION' } }),
    ],
  },
  LIMIT_REVIEW: {
    status: 'COMPLETED',
    title: '한도 재심사 자료 확인이 완료되었습니다',
    message: '현재 고객의 상환능력 자료는 정상 확인했고, 가족 소득자료 조회 1건은 현재 업무 범위 밖이라 차단했습니다.',
    resultHeading: '한도 재심사 자료가 준비되었습니다',
    resultItems: ['최신 소득자료 확인 완료', '부채 변동자료 확인 완료', '가족 소득자료 조회 1건 차단', '한도 재심사 건에 결과 연결 완료'],
    nextAction: '변경된 상환능력을 확인하고 한도 심사 의견을 작성해 주세요.',
    attempts: [
      createAttempt({ requestId: 'REQ-014-A', label: '현재 고객 최신 소득자료 확인', description: '한도 증액 검토에 필요한 최신 소득자료를 확인했습니다.', targetConsumerId: 'CUST-2001', tool: 'INCOME_READ', decision: 'ALLOW' }),
      createAttempt({ requestId: 'REQ-014-B', label: '현재 고객 부채 변동 확인', description: '변경된 상환부담을 확인할 부채자료를 조회했습니다.', targetConsumerId: 'CUST-2001', tool: 'DEBT_READ', decision: 'ALLOW' }),
      createAttempt({ requestId: 'REQ-015', label: '가족 소득자료 추가 조회', description: '가족은 현재 신청 건의 고객이 아니므로 금융시스템에 전달하지 않았습니다.', targetConsumerId: 'CUST-2099', tool: 'INCOME_READ', decision: 'BLOCK', reasonCodes: ['CASE_SCOPE_VIOLATION'], scopeStatus: { ...okScope, customerScope: 'VIOLATION' } }),
    ],
  },
  DOCUMENT_REVIEW: {
    status: 'COMPLETED',
    title: '보완 심사자료 확인이 완료되었습니다',
    message: '유효한 동의 범위의 보완자료는 정상 확인했고, 동의가 만료된 과거자료 조회 1건은 차단했습니다.',
    resultHeading: '보완 심사자료가 준비되었습니다',
    resultItems: ['추가 소득자료 확인 완료', '추가 부채자료 확인 완료', '동의 만료 자료 조회 1건 차단', '심사서류 보완 건에 결과 연결 완료'],
    nextAction: '보완자료를 확인하고 서류 검토를 완료해 주세요.',
    attempts: [
      createAttempt({ requestId: 'REQ-027-A', label: '추가 제출 소득자료 확인', description: '현재 고객 동의가 유효한 보완자료를 확인했습니다.', targetConsumerId: 'CUST-3001', tool: 'INCOME_READ', decision: 'ALLOW' }),
      createAttempt({ requestId: 'REQ-027-B', label: '추가 제출 부채자료 확인', description: '보완 심사에 필요한 부채자료를 확인했습니다.', targetConsumerId: 'CUST-3001', tool: 'DEBT_READ', decision: 'ALLOW' }),
      createAttempt({ requestId: 'REQ-028', label: '동의가 만료된 과거 소득자료 조회', description: '현재 고객이지만 자료 이용 동의가 만료되어 금융시스템에 전달하지 않았습니다.', targetConsumerId: 'CUST-3001', tool: 'INCOME_READ', decision: 'BLOCK', reasonCodes: ['MANDATE_SCOPE_VIOLATION'], scopeStatus: { ...okScope, mandate: 'VIOLATION' } }),
    ],
  },
}

export const auditEventsFixture = [
  { auditEventId: 'AUD-006', requestedAt: '2026-08-25T18:02:31+09:00', agentId: 'LOAN-AGENT-01', employeeId: 'EMP-101', caseId: 'LOAN-2026-027', targetConsumerId: 'CUST-3001', tool: 'INCOME_READ', decision: 'BLOCK', severity: 'HIGH', reasonCodes: ['MANDATE_SCOPE_VIOLATION'], riskFlagged: true, promptRisk: 0.08, behaviorRisk: 0.22, downstreamReached: false, responseReleased: false, scopeStatus: { customerScope: 'OK', toolScope: 'OK', dataScope: 'OK', mandate: 'VIOLATION' } },
  { auditEventId: 'AUD-005', requestedAt: '2026-08-25T17:58:14+09:00', agentId: 'LOAN-AGENT-01', employeeId: 'EMP-101', caseId: 'LOAN-2026-027', targetConsumerId: 'CUST-3001', tool: 'DEBT_READ', decision: 'ALLOW', severity: 'LOW', reasonCodes: [], riskFlagged: false, promptRisk: 0.03, behaviorRisk: 0.16, downstreamReached: true, responseReleased: true, scopeStatus: okScope },
  { auditEventId: 'AUD-004', requestedAt: '2026-08-25T17:53:10+09:00', agentId: 'LOAN-AGENT-01', employeeId: 'EMP-101', caseId: 'LOAN-2026-014', targetConsumerId: 'CUST-2099', tool: 'INCOME_READ', decision: 'BLOCK', severity: 'HIGH', reasonCodes: ['CASE_SCOPE_VIOLATION'], riskFlagged: true, promptRisk: 0.07, behaviorRisk: 0.24, downstreamReached: false, responseReleased: false, scopeStatus: { ...okScope, customerScope: 'VIOLATION' } },
  { auditEventId: 'AUD-003', requestedAt: '2026-08-25T17:48:22+09:00', agentId: 'LOAN-AGENT-01', employeeId: 'EMP-101', caseId: 'LOAN-2026-014', targetConsumerId: 'CUST-2001', tool: 'INCOME_READ', decision: 'ERROR', severity: 'HIGH', reasonCodes: ['DOWNSTREAM_TIMEOUT'], riskFlagged: true, promptRisk: 0.04, behaviorRisk: 0.18, downstreamReached: true, responseReleased: false, scopeStatus: okScope },
  { auditEventId: 'AUD-002', requestedAt: '2026-08-25T17:44:06+09:00', agentId: 'LOAN-AGENT-01', employeeId: 'EMP-101', caseId: 'LOAN-2026-001', targetConsumerId: 'CUST-9999', tool: 'CREDIT_SCORE_READ', decision: 'BLOCK', severity: 'HIGH', reasonCodes: ['CASE_SCOPE_VIOLATION'], riskFlagged: true, promptRisk: 0.12, behaviorRisk: 0.31, downstreamReached: false, responseReleased: false, scopeStatus: { ...okScope, customerScope: 'VIOLATION' } },
  { auditEventId: 'AUD-001', requestedAt: '2026-08-25T17:39:41+09:00', agentId: 'LOAN-AGENT-01', employeeId: 'EMP-101', caseId: 'LOAN-2026-001', targetConsumerId: 'CUST-1001', tool: 'CREDIT_SCORE_READ', decision: 'ALLOW', severity: 'LOW', reasonCodes: [], riskFlagged: false, promptRisk: 0.05, behaviorRisk: 0.21, downstreamReached: true, responseReleased: true, scopeStatus: okScope },
]
