import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import App from './App.vue'
import { finguardApi } from './services/finguardApi'

describe('FinGuard P0 application', () => {
  it('shows the bank workflow and keeps permission evidence in the Agent screen', async () => {
    const wrapper = mount(App)
    await flushPromises()

    expect(wrapper.findAll('.nav-item')).toHaveLength(2)
    expect(wrapper.text()).not.toContain('권한 비교')
    expect(wrapper.text()).toContain('직장인 신용대출 신규 심사')
    expect(wrapper.text()).toContain('CUST-1001 · CUST-9999')
    expect(wrapper.text()).toContain('Agent Effective Permission')
    expect(wrapper.text()).toContain('Employee Authority')
    expect(wrapper.text()).toContain('AI는 담당 직원보다 더 많은 정보에 접근할 수 없습니다')
  })

  it('allows an in-scope Agent task and reaches downstream once', async () => {
    const wrapper = mount(App)
    await flushPromises()

    await wrapper.get('.agent-task-form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('심사자료 확인이 완료되었습니다')
    expect(wrapper.text()).toContain('POLICY_REQUIREMENTS_MET')
    expect(wrapper.text()).toContain('완료 · 1회')
    expect(wrapper.text()).toContain('심사 의견을 작성해 주세요')
  })

  it('blocks an out-of-case customer before the financial API', async () => {
    const wrapper = mount(App)
    await flushPromises()

    await wrapper.get('input[value="OUT_OF_SCOPE"]').setValue()
    await wrapper.get('.agent-task-form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('현재 신청 건과 관련 없는 자료는 확인하지 않았습니다')
    expect(wrapper.text()).toContain('CASE_SCOPE_VIOLATION')
    expect(wrapper.text()).toContain('조회 안 함 · 0회')
    expect(wrapper.text()).toContain('현재 신청 고객의 심사자료 확인')
  })

  it('fails closed for an unsupported Agent task scenario', async () => {
    await expect(finguardApi.executeAgentTask({ scenario: 'UNKNOWN' })).rejects.toThrow(
      'Unsupported Agent task scenario',
    )
  })

  it('filters dashboard events by decision', async () => {
    const wrapper = mount(App)

    await wrapper.get('[data-screen="dashboard"]').trigger('click')
    await flushPromises()

    const select = wrapper.find('.filter-bar select')
    await select.setValue('BLOCK')

    expect(wrapper.findAll('.event-row')).toHaveLength(1)
    expect(wrapper.text()).toContain('CUST-9999')
    expect(wrapper.text()).toContain('CASE_SCOPE_VIOLATION')
    expect(wrapper.text()).toContain('현재 대출 신청 건과 관련 없는 고객 자료')
    expect(wrapper.text()).toContain('금융시스템 조회')
  })
})
