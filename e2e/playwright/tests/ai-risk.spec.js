import { expect, test } from '@playwright/test'

const aiBaseUrl = process.env.AI_RISK_BASE_URL ?? 'http://127.0.0.1:8000'
const aiCredential = process.env.E2E_AI_CREDENTIAL ?? 'e2e-ai-service-credential'

const requestBody = {
  requestId: 'REQ-E2E-AI-001',
  agentId: 'credit-review-agent',
  agentRunId: 'RUN-E2E-AI-001',
  history: [],
  currentAttempt: {
    caseId: 'CASE-2026-001',
    targetConsumerId: 'CUST-1001',
    tool: 'CREDIT_SCORE_READ',
    requestedData: ['CREDIT_SCORE'],
    requestedAt: '2026-09-01T12:00:00Z',
  },
}

test('AI 컨테이너가 준비 상태와 인증 경계를 지킨다', async ({ request }) => {
  const health = await request.get(`${aiBaseUrl}/health`)
  expect(health.status()).toBe(200)
  expect(await health.json()).toEqual({ status: 'UP' })

  const ready = await request.get(`${aiBaseUrl}/ready`)
  expect(ready.status()).toBe(200)
  expect(await ready.json()).toEqual({ status: 'READY' })

  const unauthenticated = await request.post(`${aiBaseUrl}/internal/v1/risk/behavior`, { data: requestBody })
  expect(unauthenticated.status()).toBe(401)

  const evaluated = await request.post(`${aiBaseUrl}/internal/v1/risk/behavior`, {
    data: requestBody,
    headers: { 'X-FinGuard-Service-Credential': aiCredential },
  })
  expect(evaluated.status()).toBe(200)

  const signal = await evaluated.json()
  expect(signal).toMatchObject({ historyStatus: 'COLD_START' })
  expect(signal.behaviorRisk).toBeGreaterThanOrEqual(0)
  expect(signal.behaviorRisk).toBeLessThanOrEqual(1)
  expect(signal).not.toHaveProperty('decision')
  expect(signal).not.toHaveProperty('allowed')
})
