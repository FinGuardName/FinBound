import { expect, test } from '@playwright/test'

const aiBaseUrl = process.env.AI_RISK_BASE_URL
const aiCredential = process.env.E2E_AI_CREDENTIAL
const opaBaseUrl = process.env.OPA_BASE_URL

test('AI readiness, internal authentication, and risk-signal-only contract', async ({ request }) => {
  expect(await (await request.get(`${aiBaseUrl}/health`)).json()).toEqual({ status: 'UP' })
  expect(await (await request.get(`${aiBaseUrl}/ready`)).json()).toEqual({ status: 'READY' })

  const body = {
    requestId: crypto.randomUUID(),
    agentId: 'LOAN-AGENT-01',
    agentRunId: `RUN-E2E-${crypto.randomUUID()}`,
    history: [],
    currentAttempt: {
      caseId: 'CASE-2026-001',
      targetConsumerId: 'CUST-1001',
      tool: 'CREDIT_SCORE_READ',
      requestedData: ['CREDIT_SCORE'],
      requestedAt: new Date().toISOString(),
    },
  }
  expect((await request.post(`${aiBaseUrl}/internal/v1/risk/behavior`, { data: body })).status()).toBe(401)
  const evaluated = await request.post(`${aiBaseUrl}/internal/v1/risk/behavior`, {
    data: body,
    headers: { 'X-FinGuard-Service-Credential': aiCredential },
  })
  expect(evaluated.ok()).toBeTruthy()
  const signal = await evaluated.json()
  expect(signal.behaviorRisk).toBeGreaterThanOrEqual(0)
  expect(signal.behaviorRisk).toBeLessThanOrEqual(1)
  expect(signal).not.toHaveProperty('decision')
  expect(signal).not.toHaveProperty('allowed')
})

test('real AI behavior CRITICAL signal is blocked by real OPA policy', async ({ request }) => {
  const now = new Date('2026-08-17T23:00:00Z')
  const history = Array.from({ length: 18 }, (_, index) => ({
    requestId: `REQ-E2E-BEHAVIOR-${index}`,
    caseId: 'LOAN-2026-E2E',
    targetConsumerId: 'CUST-1001',
    tool: 'CREDIT_SCORE_READ',
    requestedData: ['CREDIT_SCORE'],
    requestedAt: new Date(now.getTime() - ((index + 1) * 2_000)).toISOString(),
    decision: 'ALLOW',
    success: true,
    latencyMs: 100,
  }))
  const evaluated = await request.post(`${aiBaseUrl}/internal/v1/risk/behavior`, {
    headers: { 'X-FinGuard-Service-Credential': aiCredential },
    data: {
      requestId: 'REQ-E2E-BEHAVIOR-CURRENT',
      agentId: 'LOAN-AGENT-01',
      agentRunId: 'RUN-E2E-BEHAVIOR',
      history,
      currentAttempt: {
        caseId: 'LOAN-2026-E2E',
        targetConsumerId: 'CUST-1001',
        tool: 'CREDIT_SCORE_READ',
        requestedData: ['CREDIT_SCORE'],
        requestedAt: now.toISOString(),
      },
    },
  })
  expect(evaluated.ok()).toBeTruthy()
  const signal = await evaluated.json()
  expect(signal).toMatchObject({
    behaviorRiskLevel: 'CRITICAL',
    isAnomaly: true,
    historyStatus: 'READY',
  })

  const policyResponse = await request.post(
    `${opaBaseUrl}/v1/data/finguard/authorization/decision`,
    {
      data: {
        input: {
          scopeStatus: {
            employeeAuthority: 'OK',
            permissionTemplate: 'OK',
            caseStatus: 'OK',
            mandate: 'OK',
            passportStatus: 'OK',
            agentBinding: 'OK',
            customerScope: 'OK',
            toolScope: 'OK',
            dataScope: 'OK',
          },
          risk: {
            promptRiskLevel: 'LOW',
            promptInjectionDetected: false,
            behaviorRiskLevel: signal.behaviorRiskLevel,
          },
          limits: { hardRequestLimitExceeded: false },
        },
      },
    },
  )
  expect(policyResponse.ok()).toBeTruthy()
  expect((await policyResponse.json()).result).toMatchObject({
    decision: 'BLOCK',
    reasonCodes: ['BEHAVIOR_ANOMALY'],
    riskFlagged: true,
  })
})
