import { agentExecutionFixtures, auditEventsFixture, bankWorkCatalogFixture } from '../mock/fixtures'

const clone = (value) => structuredClone(value)

// Mock data stays behind the same boundary that future Spring API clients will implement.
export const finguardApi = {
  async getBankWorkCatalog() { return clone(bankWorkCatalogFixture) },
  async executeAgentTask({ workId, scenario }) {
    const execution = agentExecutionFixtures[workId]?.[scenario]
    if (!execution) throw new Error('Unsupported Agent task scenario')
    return clone(execution)
  },
  async getAuditEvents() { return clone(auditEventsFixture) },
}
