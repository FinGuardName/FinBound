export const agentRunFixture = {
  agentRunId: 'RUN-001', agentId: 'LOAN-AGENT-01', employeeId: 'EMP-101',
  consumerId: 'CUST-1001', caseId: 'LOAN-2026-001', passportId: 'PASS-001',
  taskType: 'LOAN_REVIEW', status: 'RUNNING', expiresAt: '2026-08-22T18:30:00+09:00',
  allowedTools: ['CREDIT_SCORE_READ', 'INCOME_READ', 'DEBT_READ'],
  allowedData: ['CREDIT_SCORE', 'INCOME', 'DEBT'],
}

export const permissionFixture = {
  employee: { id: 'EMP-101', customerScope: 'ALL', tools: ['CREDIT_SCORE_READ', 'INCOME_READ', 'DEBT_READ'], data: ['CREDIT_SCORE', 'INCOME', 'DEBT'], status: 'ACTIVE' },
  agent: { id: 'LOAN-AGENT-01', customerScope: 'CUST-1001', tools: ['CREDIT_SCORE_READ', 'INCOME_READ', 'DEBT_READ'], data: ['CREDIT_SCORE', 'INCOME', 'DEBT'], status: 'ACTIVE', passportId: 'PASS-001', expiresAt: '2026-08-22T18:30:00+09:00' },
}

export const auditEventsFixture = [
  { auditEventId: 'AUD-003', requestedAt: '2026-08-22T17:48:22+09:00', agentId: 'LOAN-AGENT-01', employeeId: 'EMP-101', caseId: 'LOAN-2026-001', targetConsumerId: 'CUST-1001', tool: 'INCOME_READ', decision: 'ERROR', severity: 'HIGH', reasonCodes: ['DOWNSTREAM_TIMEOUT'], riskFlagged: true, promptRisk: 0.04, behaviorRisk: 0.18, downstreamReached: true, responseReleased: false, scopeStatus: { customerScope: 'OK', toolScope: 'OK', dataScope: 'OK', mandate: 'OK' } },
  { auditEventId: 'AUD-002', requestedAt: '2026-08-22T17:44:06+09:00', agentId: 'LOAN-AGENT-01', employeeId: 'EMP-101', caseId: 'LOAN-2026-001', targetConsumerId: 'CUST-9999', tool: 'CREDIT_SCORE_READ', decision: 'BLOCK', severity: 'HIGH', reasonCodes: ['CASE_SCOPE_VIOLATION'], riskFlagged: true, promptRisk: 0.12, behaviorRisk: 0.31, downstreamReached: false, responseReleased: false, scopeStatus: { customerScope: 'VIOLATION', toolScope: 'OK', dataScope: 'OK', mandate: 'OK' } },
  { auditEventId: 'AUD-001', requestedAt: '2026-08-22T17:39:41+09:00', agentId: 'LOAN-AGENT-01', employeeId: 'EMP-101', caseId: 'LOAN-2026-001', targetConsumerId: 'CUST-1001', tool: 'CREDIT_SCORE_READ', decision: 'ALLOW', severity: 'LOW', reasonCodes: [], riskFlagged: false, promptRisk: 0.05, behaviorRisk: 0.21, downstreamReached: true, responseReleased: true, scopeStatus: { customerScope: 'OK', toolScope: 'OK', dataScope: 'OK', mandate: 'OK' } },
]
