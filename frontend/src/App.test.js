import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'

import App from './App.vue'
import { configureFinboundApi, finboundApi, resetFinboundApi } from './services/finboundApi'

afterEach(() => resetFinboundApi())

const getAllAuditEvents = async () => {
  const result = await finboundApi.getAuditEvents({ filters: { period: 'ALL' }, pageSize: 100 })
  return result.items
}

describe('FinBound P0 application', () => {
  it('keeps the real Core credential in memory instead of Web Storage', async () => {
    const storageWrite = vi.spyOn(Storage.prototype, 'setItem')
    configureFinboundApi({ mode: 'real' })
    const wrapper = mount(App)

    expect(wrapper.text()).toContain('업무 세션 Credential을 입력해 주세요')
    expect(wrapper.findAll('.work-card')).toHaveLength(0)

    await wrapper.get('#core-credential').setValue('operator-runtime-only')
    await wrapper.get('.credential-panel form').trigger('submit')
    await flushPromises()

    expect(wrapper.findAll('.work-card')).toHaveLength(3)
    expect(finboundApi.hasCredential()).toBe(true)
    expect(storageWrite).not.toHaveBeenCalled()
    expect(wrapper.html()).not.toContain('operator-runtime-only')
  })

  it('shows bank work instead of asking an employee to configure security', async () => {
    const wrapper = mount(App)
    await flushPromises()

    expect(wrapper.findAll('.nav-item')).toHaveLength(2)
    expect(wrapper.findAll('.work-card')).toHaveLength(3)
    expect(wrapper.text()).not.toContain('권한 비교')
    expect(wrapper.text()).toContain('직장인 신용대출 신규 심사')
    expect(wrapper.text()).toContain('CUST-1001 · CUST-2001 · CUST-3001 외')
    expect(wrapper.text()).toContain('Agent Effective Permission')
    expect(wrapper.text()).toContain('Employee Authority')
    expect(wrapper.text()).toContain('AI는 담당 직원보다 더 많은 정보에 접근할 수 없습니다')
    expect(wrapper.text()).toContain('허용 자료')
    expect(wrapper.text()).toContain('CREDIT_SCORE')
    expect(wrapper.findAll('input[type="radio"]')).toHaveLength(0)
    expect(wrapper.text()).toContain('직원이 요청한 업무')
    expect(wrapper.text()).toContain('AI로 이 업무 진행')
  })

  it('completes an ordinary loan review without manufacturing a block', async () => {
    const wrapper = mount(App)
    await flushPromises()

    await wrapper.get('.agent-task-form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('신규 대출 심사자료 확인이 완료되었습니다')
    expect(wrapper.text()).toContain('3건 확인 · 0건 차단')
    expect(wrapper.text()).toContain('차단 사유 없음')
    expect(wrapper.text()).toContain('완료 · 1회')
    expect(wrapper.text()).toContain('심사 의견을 작성해 주세요')
    expect(wrapper.text()).not.toContain('CASE_SCOPE_VIOLATION')
    expect(wrapper.text()).not.toContain('FinBound 보호 작동')
    expect(wrapper.text()).not.toContain('POLICY_REQUIREMENTS_MET')
  })

  it('fails closed for an unsupported Agent task', async () => {
    await expect(finboundApi.executeAgentTask({ workId: 'UNKNOWN' })).rejects.toThrow('Unsupported Agent task')
  })

  it('does not claim downstream non-reachability when execution status is unknown', async () => {
    const executeAgentTask = vi.spyOn(finboundApi, 'executeAgentTask').mockRejectedValueOnce(new Error('network error'))
    const wrapper = mount(App)
    await flushPromises()

    await wrapper.get('.agent-task-form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('업무 처리 상태를 확인할 수 없습니다')
    expect(wrapper.text()).toContain('금융시스템 조회 여부는 업무 기록에서 확인해 주세요')
    expect(wrapper.text()).not.toContain('자료를 조회하지 않았습니다')
    executeAgentTask.mockRestore()
  })

  it('does not present an allowed policy decision with a system error as a successful check', async () => {
    const executeAgentTask = vi.spyOn(finboundApi, 'executeAgentTask').mockResolvedValueOnce({
      status: 'COMPLETED',
      title: 'AI 업무 처리 중 오류가 발생했습니다',
      message: '정상 완료로 처리하지 않았습니다.',
      resultHeading: 'Agent 실행 결과',
      resultItems: ['처리 오류 1건'],
      nextAction: '업무 기록을 확인해 주세요.',
      attempts: [{
        requestId: 'REQ-ERROR-1',
        decision: 'ALLOW',
        systemOutcome: 'ERROR',
        label: '신용정보 확인',
        description: '업무 시스템 처리 중 오류가 발생했습니다.',
        targetConsumerId: 'CUST-1001',
        scopeStatus: { customerScope: 'OK' },
        reasonCodes: ['DOWNSTREAM_ERROR'],
        downstreamReached: null,
        responseReleased: false,
        tool: 'CREDIT_SCORE_READ',
        requestedData: ['CREDIT_SCORE'],
      }],
    })
    const wrapper = mount(App)
    await flushPromises()

    await wrapper.get('.agent-task-form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('업무 오류')
    expect(wrapper.text()).toContain('0건 확인 · 0건 차단 · 1건 오류')
    expect(wrapper.text()).toContain('처리 오류')
    expect(wrapper.text()).toContain('금융시스템 조회확인 불가')
    expect(wrapper.text()).not.toContain('1건 확인 · 0건 차단')
    executeAgentTask.mockRestore()
  })

  it.each([
    ['NEW_LOAN', 3, 0, null],
    ['LIMIT_REVIEW', 2, 1, 'CASE_SCOPE_VIOLATION'],
    ['DOCUMENT_REVIEW', 2, 1, 'MANDATE_SCOPE_VIOLATION'],
  ])('keeps %s task-level permission enforcement contract', async (workId, allowedCount, blockedCount, reasonCode) => {
    const result = await finboundApi.executeAgentTask({ workId })
    const allowed = result.attempts.filter((attempt) => attempt.decision === 'ALLOW')
    const blocked = result.attempts.filter((attempt) => attempt.decision === 'BLOCK')

    expect(result.status).toBe('COMPLETED')
    expect(allowed).toHaveLength(allowedCount)
    expect(allowed.every((attempt) => attempt.downstreamReached && attempt.responseReleased)).toBe(true)
    expect(blocked).toHaveLength(blockedCount)
    expect(blocked.every((attempt) => !attempt.downstreamReached && !attempt.responseReleased)).toBe(true)
    if (reasonCode) expect(blocked[0].reasonCodes).toContain(reasonCode)
  })

  it('shows an automatically blocked extra lookup during limit review', async () => {
    const wrapper = mount(App)
    await flushPromises()

    await wrapper.get('[data-work="LIMIT_REVIEW"]').trigger('click')
    expect(wrapper.text()).toContain('신용대출 한도 증액 재심사')
    expect(wrapper.text()).toContain('CUST-2001')

    await wrapper.get('.agent-task-form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('한도 재심사 자료 확인이 완료되었습니다')
    expect(wrapper.text()).toContain('최신 소득자료 확인 완료')
    expect(wrapper.text()).toContain('가족 소득자료 추가 조회')
    expect(wrapper.text()).toContain('AI가 참고자료로 가족 소득을 추가 확인하려 했지만')
    expect(wrapper.text()).toContain('CASE_SCOPE_VIOLATION')
    expect(wrapper.text()).toContain('2건 확인 · 1건 차단')
  })

  it('blocks expired-consent data in the document review simulation', async () => {
    const wrapper = mount(App)
    await flushPromises()

    await wrapper.get('[data-work="DOCUMENT_REVIEW"]').trigger('click')
    await wrapper.get('.agent-task-form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('보완 심사자료 확인이 완료되었습니다')
    expect(wrapper.text()).toContain('동의가 만료된 과거 소득자료 조회')
    expect(wrapper.text()).toContain('MANDATE_SCOPE_VIOLATION')
    expect(wrapper.text()).toContain('차단 · 0회')
    expect(wrapper.text()).toContain('FinBound 보호 작동')
  })

  it('keeps policy decision and system error as separate audit facts', async () => {
    const events = await getAllAuditEvents()
    const timeoutEvent = events.find((event) => event.auditStatus === 'ERROR')

    expect(timeoutEvent.decision).toBe('ALLOW')
    expect(timeoutEvent.reasonCodes).toContain('DOWNSTREAM_TIMEOUT')
    expect(timeoutEvent.downstreamReached).toBe(true)
    expect(timeoutEvent.responseReleased).toBe(false)
    expect(events.every((event) => ['ALLOW', 'BLOCK'].includes(event.decision))).toBe(true)
  })

  it('blocks an AI behavior anomaly even when all permission scopes are valid', async () => {
    const events = await getAllAuditEvents()
    const behaviorBlock = events.find((event) => event.reasonCodes.includes('BEHAVIOR_ANOMALY'))

    expect(Object.keys(behaviorBlock.scopeStatus)).toHaveLength(9)
    expect(Object.values(behaviorBlock.scopeStatus).every((value) => value === 'OK')).toBe(true)
    expect(behaviorBlock.behaviorAnomalyDetected).toBe(true)
    expect(behaviorBlock.behaviorRiskLevel).toBe('CRITICAL')
    expect(behaviorBlock.decision).toBe('BLOCK')
    expect(behaviorBlock.downstreamReached).toBe(false)
  })

  it('records prompt injection as an independent AI risk signal', async () => {
    const events = await getAllAuditEvents()
    const promptBlock = events.find((event) => event.reasonCodes.includes('PROMPT_INJECTION'))

    expect(promptBlock.promptInjectionDetected).toBe(true)
    expect(promptBlock.promptEvaluationStatus).toBe('EVALUATED')
    expect(promptBlock.promptModelVersion).toBeTruthy()
    expect(promptBlock.decision).toBe('BLOCK')
    expect(promptBlock.downstreamReached).toBe(false)
  })

  it('keeps transitional prompt records explicitly unevaluated', async () => {
    const events = await getAllAuditEvents()
    const unevaluated = events.find((event) => event.promptEvaluationStatus === 'NOT_EVALUATED')

    expect(unevaluated.promptRisk).toBe(0)
    expect(unevaluated.promptInjectionDetected).toBe(false)
    expect(unevaluated.promptModelVersion).toBeNull()
  })

  it('uses behavior model levels without deriving thresholds in the browser', async () => {
    const events = await getAllAuditEvents()
    const alert = events.find((event) => event.behaviorRiskLevel === 'ALERT')
    const critical = events.find((event) => event.behaviorRiskLevel === 'CRITICAL')

    expect(alert.behaviorRisk).toBe(0.95)
    expect(critical.behaviorRisk).toBe(1)
  })

  it('supports every agreed dashboard filter and derived outcome', async () => {
    const wrapper = mount(App)

    await wrapper.get('[data-screen="dashboard"]').trigger('click')
    await flushPromises()

    expect(wrapper.findAll('[data-filter]')).toHaveLength(8)
    await wrapper.get('[data-filter="outcome"]').setValue('BLOCK')
    await flushPromises()

    expect(wrapper.findAll('.event-row')).toHaveLength(5)
    expect(wrapper.text()).toContain('BEHAVIOR_ANOMALY')
    expect(wrapper.text()).toContain('평소 업무 흐름과 다른 AI 행동')
    expect(wrapper.text()).toContain('금융시스템 조회')
    expect(wrapper.text()).toContain('판단 근거와 버전 정보')
  })

  it('paginates dashboard records without rendering an unbounded list', async () => {
    const wrapper = mount(App)

    await wrapper.get('[data-screen="dashboard"]').trigger('click')
    await flushPromises()

    expect(wrapper.findAll('.event-row')).toHaveLength(5)
    expect(wrapper.text()).toContain('1 / 2 페이지')

    await wrapper.get('.pagination button:last-child').trigger('click')
    await flushPromises()

    expect(wrapper.findAll('.event-row')).toHaveLength(4)
    expect(wrapper.text()).toContain('2 / 2 페이지')
  })

  it('renders historical prompt records as unevaluated instead of safe', async () => {
    const wrapper = mount(App)

    await wrapper.get('[data-screen="dashboard"]').trigger('click')
    await flushPromises()
    await wrapper.get('.pagination button:last-child').trigger('click')
    await flushPromises()
    await wrapper.findAll('.event-row').at(-1).trigger('click')

    expect(wrapper.findAll('.risk-meter')[0].text()).toContain('미평가')
    expect(wrapper.text()).toContain('입력 모델미평가')
  })

  it('supports MEDIUM severity and server-style pagination at the API boundary', async () => {
    const firstPage = await finboundApi.getAuditEvents({ filters: { period: 'ALL' }, page: 1, pageSize: 5 })
    const secondPage = await finboundApi.getAuditEvents({ filters: { period: 'ALL' }, page: 2, pageSize: 5 })
    const mediumOnly = await finboundApi.getAuditEvents({ filters: { period: 'ALL', severity: 'MEDIUM' } })

    expect(firstPage.items).toHaveLength(5)
    expect(firstPage.totalItems).toBe(9)
    expect(firstPage.totalPages).toBe(2)
    expect(secondPage.items).toHaveLength(4)
    expect(mediumOnly.items).toHaveLength(1)
    expect(mediumOnly.items[0].severity).toBe('MEDIUM')
  })

  it('uses UUID request identifiers and never invents an allow reason code', async () => {
    const executions = await Promise.all(['NEW_LOAN', 'LIMIT_REVIEW', 'DOCUMENT_REVIEW'].map((workId) => finboundApi.executeAgentTask({ workId })))
    const events = await getAllAuditEvents()
    const requestIds = [...executions.flatMap((execution) => execution.attempts.map((attempt) => attempt.requestId)), ...events.map((event) => event.requestId)]
    const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-8[0-9a-f]{3}-[0-9a-f]{12}$/i

    expect(requestIds.every((requestId) => uuidPattern.test(requestId))).toBe(true)
    expect(events.flatMap((event) => event.reasonCodes)).not.toContain('POLICY_REQUIREMENTS_MET')
  })
})
