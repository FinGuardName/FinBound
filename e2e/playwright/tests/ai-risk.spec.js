import { expect, test } from '@playwright/test'

const aiBaseUrl = process.env.AI_RISK_BASE_URL
const aiCredential = process.env.E2E_AI_CREDENTIAL

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
