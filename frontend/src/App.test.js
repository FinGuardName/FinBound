import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import App from './App.vue'
import { finguardApi } from './services/finguardApi'

describe('FinGuard P0 application', () => {
  it('shows the bank workflow and keeps permission evidence in the Agent screen', async () => {
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
    expect(wrapper.findAll('input[type="radio"]')).toHaveLength(0)
    expect(wrapper.text()).toContain('직원이 요청한 업무')
    expect(wrapper.text()).toContain('AI로 이 업무 진행')
  })

  it('continues the employee task while blocking an unnecessary Agent access', async () => {
    const wrapper = mount(App)
    await flushPromises()

    await wrapper.get('.agent-task-form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('심사자료 확인이 완료되었습니다')
    expect(wrapper.text()).toContain('POLICY_REQUIREMENTS_MET')
    expect(wrapper.text()).toContain('완료 · 1회')
    expect(wrapper.text()).toContain('CASE_SCOPE_VIOLATION')
    expect(wrapper.text()).toContain('차단 · 0회')
    expect(wrapper.text()).toContain('3건 확인 · 1건 차단')
    expect(wrapper.text()).toContain('심사 의견을 작성해 주세요')
  })

  it('fails closed for an unsupported Agent task', async () => {
    await expect(finguardApi.executeAgentTask({ workId: 'UNKNOWN' })).rejects.toThrow('Unsupported Agent task')
  })

  it.each([
    ['NEW_LOAN', 3, 'CASE_SCOPE_VIOLATION'],
    ['LIMIT_REVIEW', 2, 'CASE_SCOPE_VIOLATION'],
    ['DOCUMENT_REVIEW', 2, 'MANDATE_SCOPE_VIOLATION'],
  ])('keeps %s task-level permission enforcement contract', async (workId, allowedCount, reasonCode) => {
    const result = await finguardApi.executeAgentTask({ workId })
    const allowed = result.attempts.filter((attempt) => attempt.decision === 'ALLOW')
    const blocked = result.attempts.filter((attempt) => attempt.decision === 'BLOCK')

    expect(result.status).toBe('COMPLETED')
    expect(allowed).toHaveLength(allowedCount)
    expect(allowed.every((attempt) => attempt.downstreamReached)).toBe(true)
    expect(blocked).toHaveLength(1)
    expect(blocked[0].downstreamReached).toBe(false)
    expect(blocked[0].reasonCodes).toContain(reasonCode)
  })

  it('runs a separate limit review simulation', async () => {
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
    expect(wrapper.text()).toContain('CASE_SCOPE_VIOLATION')
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
  })

  it('filters dashboard events by decision', async () => {
    const wrapper = mount(App)

    await wrapper.get('[data-screen="dashboard"]').trigger('click')
    await flushPromises()

    const select = wrapper.find('.filter-bar select')
    await select.setValue('BLOCK')

    expect(wrapper.findAll('.event-row')).toHaveLength(3)
    expect(wrapper.text()).toContain('CUST-3001')
    expect(wrapper.text()).toContain('MANDATE_SCOPE_VIOLATION')
    expect(wrapper.text()).toContain('현재 고객 동의 범위')
    expect(wrapper.text()).toContain('금융시스템 조회')
  })
})
