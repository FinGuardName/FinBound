import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import App from './App.vue'

describe('FinGuard P0 application', () => {
  it('creates an AgentRun and shows its minimum permission', async () => {
    const wrapper = mount(App)

    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('PASS-001')
    expect(wrapper.text()).toContain('LOAN-2026-001')
    expect(wrapper.text()).toContain('Agent Effective Permission ⊆ Employee Authority')
  })

  it('shows the narrowed consumer scope in the comparison screen', async () => {
    const wrapper = mount(App)

    await wrapper.get('button:nth-of-type(2)').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Authority ceiling')
    expect(wrapper.text()).toContain('CUST-1001')
    expect(wrapper.text()).toContain('CASE BOUND')
  })

  it('filters dashboard events by decision', async () => {
    const wrapper = mount(App)

    await wrapper.get('button:nth-of-type(3)').trigger('click')
    await flushPromises()

    const select = wrapper.find('.filter-bar select')
    await select.setValue('BLOCK')

    expect(wrapper.findAll('.event-row')).toHaveLength(1)
    expect(wrapper.text()).toContain('CUST-9999')
    expect(wrapper.text()).toContain('CASE_SCOPE_VIOLATION')
    expect(wrapper.text()).toContain('Downstream reached')
  })
})
