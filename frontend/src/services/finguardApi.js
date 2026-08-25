import { agentExecutionFixtures, auditEventsFixture, bankWorkContextFixture } from '../mock/fixtures'

const clone = (value) => structuredClone(value)

// Mock data stays behind the same boundary that future Spring API clients will implement.
export const finguardApi = {
  async getBankWorkContext() { return clone(bankWorkContextFixture) },
  async executeAgentTask({ scenario }) {
    const execution = agentExecutionFixtures[scenario]
    if (!execution) throw new Error('Unsupported Agent task scenario')
    return clone(execution)
  },
  async getAuditEvents() { return clone(auditEventsFixture) },
}
