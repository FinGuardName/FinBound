import { agentExecutionFixtures, auditEventsFixture, bankWorkCatalogFixture } from '../mock/fixtures'

const clone = (value) => structuredClone(value)

// Mock data stays behind the same boundary that the future Spring Core adapter will implement.
// getBankWorkCatalog is presentation configuration. executeAgentTask maps to the AgentRun
// command and its permission-comparison/audit results; getAuditEvents maps to read-only
// audit and dashboard queries. The browser never calls OPA, AI, or downstream tools directly.
export const finguardApi = {
  async getBankWorkCatalog() { return clone(bankWorkCatalogFixture) },
  async executeAgentTask({ workId }) {
    const execution = agentExecutionFixtures[workId]
    if (!execution) throw new Error('Unsupported Agent task')
    return clone(execution)
  },
  async getAuditEvents() { return clone(auditEventsFixture) },
}
