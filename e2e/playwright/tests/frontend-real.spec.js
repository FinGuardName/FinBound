import { expect, test } from '@playwright/test'

const operatorCredential = process.env.E2E_OPERATOR_CREDENTIAL ?? 'e2e-operator-credential'
const forbiddenMarkers = [
  operatorCredential,
  'E2E_RAW_PROMPT_MUST_NOT_RENDER',
  'E2E_FINANCIAL_PAYLOAD_MUST_NOT_RENDER',
]

async function connectToCore(page) {
  await page.goto('/')
  await expect(page.getByText('Core API 연결 모드')).toBeVisible()
  await page.locator('#core-credential').fill(operatorCredential)
  await page.getByRole('button', { name: 'Core API 연결' }).click()
  await expect(page.getByRole('button', { name: 'AI로 이 업무 진행' })).toBeVisible()
}

test('Real Adapter로 AgentRun을 만들고 감사 차단 내역을 조회한다', async ({ page }) => {
  await connectToCore(page)

  await page.getByRole('button', { name: 'AI로 이 업무 진행' }).click()
  await expect(page.getByRole('heading', { name: 'AI 업무 실행이 등록되었습니다' })).toBeVisible()
  await expect(page.getByText('AgentRun이 생성되었습니다. Tool Call 결과는 감사 현황에서 확인할 수 있습니다.')).toBeVisible()

  await page.locator('[data-screen="dashboard"]').click()
  await expect(page.getByRole('heading', { name: 'AI 업무 안전 현황', level: 2 })).toBeVisible()
  await expect(page.locator('.event-row strong', { hasText: '김○○ 고객' })).toBeVisible()
  await expect(page.getByText('평소 업무 흐름과 다른 AI 행동을 확인해 실행 전에 차단했습니다.')).toBeVisible()
  await expect(page.getByText('조회 안 함')).toBeVisible()
  await expect(page.getByText('제공 안 함')).toBeVisible()

  const browserStorage = await page.evaluate(() => ({
    local: Object.fromEntries(Object.entries(localStorage)),
    session: Object.fromEntries(Object.entries(sessionStorage)),
  }))
  expect(browserStorage).toEqual({ local: {}, session: {} })

  const renderedText = await page.locator('body').innerText()
  for (const marker of forbiddenMarkers) expect(renderedText).not.toContain(marker)
})

test('SPA fallback과 정적 번들에 E2E Credential이 노출되지 않는다', async ({ page, request }) => {
  await page.goto('/future-dashboard-route')
  await expect(page.getByText('Core API 연결 모드')).toBeVisible()

  const htmlResponse = await request.get('/')
  expect(htmlResponse.ok()).toBeTruthy()
  const html = await htmlResponse.text()
  const scripts = [...html.matchAll(/<script[^>]+src="([^"]+\.js)"/g)].map((match) => match[1])
  expect(scripts.length).toBeGreaterThan(0)

  for (const source of scripts) {
    const scriptResponse = await request.get(source)
    expect(scriptResponse.ok()).toBeTruthy()
    const bundle = await scriptResponse.text()
    expect(bundle).not.toContain(operatorCredential)
    expect(bundle).not.toContain('FINGUARD_INTERNAL_CREDENTIAL')
  }
})
