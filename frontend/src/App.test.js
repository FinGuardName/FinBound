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
  })

  it('allows an in-scope Agent task and reaches downstream once', async () => {
    const wrapper = mount(App)
    await flushPromises()

    await wrapper.get('.agent-task-form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('허용된 업무를 안전하게 실행했습니다')
    expect(wrapper.text()).toContain('POLICY_REQUIREMENTS_MET')
    expect(wrapper.text()).toContain('YES · 1회')
  })

  it('blocks an out-of-case customer before the financial API', async () => {
    const wrapper = mount(App)
    await flushPromises()

    await wrapper.get('input[value="OUT_OF_SCOPE"]').setValue()
    await wrapper.get('.agent-task-form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('권한 외 고객 조회를 차단했습니다')
    expect(wrapper.text()).toContain('CASE_SCOPE_VIOLATION')
    expect(wrapper.text()).toContain('NO · 0회')
    expect(wrapper.text()).toContain('Downstream 보호 완료')
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
    expect(wrapper.text()).toContain('Downstream reached')
  })
})
