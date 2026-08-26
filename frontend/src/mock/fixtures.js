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
    scenarios: [
      { id: 'IN_SCOPE', icon: '✓', title: '현재 신청 고객의 심사자료 확인', description: '신용정보·소득·부채 자료를 한 번에 확인합니다.', tag: '기본' },
      { id: 'OUT_OF_SCOPE', icon: '＋', title: '유사 고객 자료까지 함께 비교', description: '현재 신청 건 외 참고 고객 자료를 포함해 요청합니다.', tag: '추가 검토' },
    ],
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
    scenarios: [
      { id: 'IN_SCOPE', icon: '✓', title: '변경된 소득·부채 자료 재확인', description: '현재 고객의 최신 상환능력 자료를 확인합니다.', tag: '기본' },
      { id: 'OUT_OF_SCOPE', icon: '＋', title: '가족 소득자료까지 함께 확인', description: '현재 신청 고객 외 가족의 소득자료를 포함해 요청합니다.', tag: '추가 검토' },
    ],
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
    scenarios: [
      { id: 'IN_SCOPE', icon: '✓', title: '제출된 보완자료 확인', description: '고객이 추가 제출한 소득·부채 자료를 확인합니다.', tag: '기본' },
      { id: 'OUT_OF_SCOPE', icon: '＋', title: '동의가 만료된 자료까지 다시 확인', description: '현재 고객 동의 범위에 포함되지 않은 과거 자료를 요청합니다.', tag: '추가 검토' },
    ],
  },
]

function createExecution({
  requestId,
  targetConsumerId,
  tool,
  decision,
  title,
  message,
  reasonCodes,
  scopeStatus,
  caseTitle,
  actionTitle,
  actionDescription,
  protectionTitle,
  protectionDescription,
  completionTitle,
  completionDescription,
  resultHeading,
  resultItems,
  nextAction,
}) {
  return {
    requestId,
    targetConsumerId,
    tool,
    decision,
    title,
    message,
    reasonCodes,
    downstreamReached: decision === 'ALLOW',
    scopeStatus,
    resultHeading,
    resultItems,
    nextAction,
    steps: [
      { id: 1, label: '업무 확인', title: '현재 여신 업무 확인', description: caseTitle, state: 'ALLOW' },
      { id: 2, label: '자료 확인', title: actionTitle, description: actionDescription, state: 'ALLOW' },
      { id: 3, label: '보호 확인', title: protectionTitle, description: protectionDescription, state: decision },
      { id: 4, label: decision === 'ALLOW' ? '확인 완료' : '안전 중단', title: completionTitle, description: completionDescription, state: decision },
    ],
  }
}

const okScope = { customerScope: 'OK', toolScope: 'OK', dataScope: 'OK', mandate: 'OK' }

export const agentExecutionFixtures = {
  NEW_LOAN: {
    IN_SCOPE: createExecution({
      requestId: 'REQ-001', targetConsumerId: 'CUST-1001', tool: 'CREDIT_SCORE_READ', decision: 'ALLOW',
      title: '신규 심사자료 확인이 완료되었습니다', message: '현재 신청 고객의 동의와 업무 범위 안에서 필요한 자료만 확인했습니다.', reasonCodes: [], scopeStatus: okScope,
      caseTitle: '김○○ 고객의 신규 대출 심사 업무에 연결했습니다.', actionTitle: 'AI가 기본 심사자료 확인 시작', actionDescription: '신용정보·소득·부채 자료 확인을 준비했습니다.', protectionTitle: '고객 동의와 업무 범위 확인 완료', protectionDescription: '현재 신청 건에 필요한 자료만 포함되어 있습니다.', completionTitle: '심사자료를 현재 신청 건에 연결', completionDescription: '확인된 결과를 신규 대출 심사 업무에 안전하게 전달했습니다.',
      resultHeading: '신규 심사자료가 준비되었습니다', resultItems: ['신용정보 확인 완료', '소득·부채 자료 확인 완료', '현재 신청 건에 결과 연결 완료'], nextAction: '확인된 자료를 바탕으로 심사 의견을 작성해 주세요.',
    }),
    OUT_OF_SCOPE: createExecution({
      requestId: 'REQ-002', targetConsumerId: 'CUST-9999', tool: 'CREDIT_SCORE_READ', decision: 'BLOCK',
      title: '현재 신청 건과 관련 없는 자료는 확인하지 않았습니다', message: '다른 고객 자료가 포함된 요청이어서 안전하게 중단했습니다. 현재 신청 고객 자료만 다시 요청해 주세요.', reasonCodes: ['CASE_SCOPE_VIOLATION'], scopeStatus: { ...okScope, customerScope: 'VIOLATION' },
      caseTitle: '김○○ 고객의 신규 대출 심사 업무에 연결했습니다.', actionTitle: 'AI가 비교자료 확인 시작', actionDescription: '현재 신청 건 외 참고 고객 자료가 포함되었습니다.', protectionTitle: '다른 고객 자료 포함 확인', protectionDescription: '현재 신청 고객이 아닌 자료가 포함되어 보호 설정이 작동했습니다.', completionTitle: '금융시스템 조회 전 요청 중단', completionDescription: '다른 고객의 자료는 조회하지 않았습니다.',
      resultHeading: '요청 범위를 다시 확인해 주세요', resultItems: ['현재 신청 고객 외 자료가 포함됨', '다른 고객 자료는 조회하지 않음', '현재 신청 건은 그대로 유지됨'], nextAction: '‘현재 신청 고객의 심사자료 확인’을 선택해 다시 실행해 주세요.',
    }),
  },
  LIMIT_REVIEW: {
    IN_SCOPE: createExecution({
      requestId: 'REQ-014', targetConsumerId: 'CUST-2001', tool: 'INCOME_READ', decision: 'ALLOW',
      title: '한도 재심사 자료 확인이 완료되었습니다', message: '현재 고객의 최신 소득·부채 자료만 확인해 한도 재심사에 연결했습니다.', reasonCodes: [], scopeStatus: okScope,
      caseTitle: '이○○ 고객의 대출 한도 재심사 업무에 연결했습니다.', actionTitle: 'AI가 최신 상환능력 자료 확인 시작', actionDescription: '변경된 소득과 부채 자료 확인을 준비했습니다.', protectionTitle: '현재 고객과 재심사 범위 확인 완료', protectionDescription: '한도 증액 검토에 필요한 자료만 포함되어 있습니다.', completionTitle: '재심사 자료를 현재 신청 건에 연결', completionDescription: '확인된 결과를 한도 재심사 업무에 안전하게 전달했습니다.',
      resultHeading: '한도 재심사 자료가 준비되었습니다', resultItems: ['최신 소득자료 확인 완료', '부채 변동자료 확인 완료', '한도 재심사 건에 결과 연결 완료'], nextAction: '변경된 상환능력을 확인하고 한도 심사 의견을 작성해 주세요.',
    }),
    OUT_OF_SCOPE: createExecution({
      requestId: 'REQ-015', targetConsumerId: 'CUST-2099', tool: 'INCOME_READ', decision: 'BLOCK',
      title: '가족의 소득자료는 확인하지 않았습니다', message: '현재 대출 재심사 고객이 아닌 가족 자료가 포함되어 조회 전에 안전하게 중단했습니다.', reasonCodes: ['CASE_SCOPE_VIOLATION'], scopeStatus: { ...okScope, customerScope: 'VIOLATION' },
      caseTitle: '이○○ 고객의 대출 한도 재심사 업무에 연결했습니다.', actionTitle: 'AI가 가족 포함 소득자료 확인 시작', actionDescription: '현재 신청 고객 외 가족의 자료가 포함되었습니다.', protectionTitle: '재심사 고객 범위 초과 확인', protectionDescription: '가족은 현재 대출 신청 건의 고객이 아닙니다.', completionTitle: '금융시스템 조회 전 요청 중단', completionDescription: '가족의 소득자료는 조회하지 않았습니다.',
      resultHeading: '현재 고객 자료만 확인해 주세요', resultItems: ['가족 소득자료가 요청에 포함됨', '가족 자료는 조회하지 않음', '한도 재심사 건은 그대로 유지됨'], nextAction: '‘변경된 소득·부채 자료 재확인’을 선택해 다시 실행해 주세요.',
    }),
  },
  DOCUMENT_REVIEW: {
    IN_SCOPE: createExecution({
      requestId: 'REQ-027', targetConsumerId: 'CUST-3001', tool: 'DEBT_READ', decision: 'ALLOW',
      title: '보완 심사자료 확인이 완료되었습니다', message: '고객이 추가 제출한 자료 중 현재 동의 범위에 포함된 내용만 확인했습니다.', reasonCodes: [], scopeStatus: okScope,
      caseTitle: '박○○ 고객의 심사서류 보완 업무에 연결했습니다.', actionTitle: 'AI가 추가 제출자료 확인 시작', actionDescription: '보완된 소득과 부채 자료 확인을 준비했습니다.', protectionTitle: '제출자료와 고객 동의 범위 확인 완료', protectionDescription: '현재 동의가 유효한 자료만 포함되어 있습니다.', completionTitle: '보완자료를 현재 심사 건에 연결', completionDescription: '확인된 자료를 심사서류 보완 업무에 안전하게 전달했습니다.',
      resultHeading: '보완 심사자료가 준비되었습니다', resultItems: ['추가 소득자료 확인 완료', '추가 부채자료 확인 완료', '심사서류 보완 건에 결과 연결 완료'], nextAction: '보완자료를 확인하고 서류 검토를 완료해 주세요.',
    }),
    OUT_OF_SCOPE: createExecution({
      requestId: 'REQ-028', targetConsumerId: 'CUST-3001', tool: 'INCOME_READ', decision: 'BLOCK',
      title: '고객 동의가 만료된 자료는 확인하지 않았습니다', message: '현재 고객이지만 자료 이용 동의가 유효하지 않아 금융시스템 조회 전에 중단했습니다.', reasonCodes: ['MANDATE_SCOPE_VIOLATION'], scopeStatus: { ...okScope, mandate: 'VIOLATION' },
      caseTitle: '박○○ 고객의 심사서류 보완 업무에 연결했습니다.', actionTitle: 'AI가 과거 자료 재확인 시작', actionDescription: '현재 고객 동의에 포함되지 않은 과거 자료가 요청되었습니다.', protectionTitle: '고객 동의 범위 초과 확인', protectionDescription: '요청한 과거 자료의 이용 동의가 만료되었습니다.', completionTitle: '금융시스템 조회 전 요청 중단', completionDescription: '동의가 만료된 자료는 조회하지 않았습니다.',
      resultHeading: '고객 동의 범위를 확인해 주세요', resultItems: ['동의가 만료된 과거 자료가 포함됨', '동의 없는 자료는 조회하지 않음', '심사서류 보완 건은 그대로 유지됨'], nextAction: '유효한 동의를 확인한 뒤 ‘제출된 보완자료 확인’을 다시 실행해 주세요.',
    }),
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
