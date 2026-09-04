import { expect, test } from '@playwright/test'

test('AI outage fails closed before the finance downstream @fail-closed', async ({ request }) => {
  const operatorCredential = process.env.E2E_OPERATOR_CREDENTIAL
  const auth = {
    Authorization: `Bearer ${operatorCredential}`,
    'Content-Type': 'application/json',
    'X-Request-Id': crypto.randomUUID(),
  }
  const created = await request.post('/core-api/api/v1/agent-runs', {
    headers: auth,
    data: {
      employeeId: 'EMP-101',
      consumerId: 'CUST-1001',
      taskType: 'LOAN_REVIEW',
      scenario: 'NORMAL_CREDIT_SCORE',
      inputText: `AI 중단 시 안전 차단 검증 ${crypto.randomUUID()}`,
    },
  })
  expect(created.status()).toBe(201)
  const run = await created.json()
  let audit
  await expect.poll(async () => {
    const response = await request.get(
      '/core-api/api/v1/audit-events?page=1&pageSize=100',
      { headers: auth },
    )
    const body = await response.json()
    audit = body.items.find((item) => item.agentRunId === run.agentRunId)
    return audit?.status
  }, { timeout: 20_000, intervals: [250, 500, 1_000] }).toBe('ERROR')

  expect(audit).toMatchObject({
    systemOutcome: 'ERROR',
    downstreamReached: false,
    responseReleased: false,
    promptRiskEvaluationStatus: 'NOT_EVALUATED',
  })
  expect(audit.decision).toBeUndefined()
})
