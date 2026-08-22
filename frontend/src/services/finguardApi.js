import { agentRunFixture, auditEventsFixture, permissionFixture } from '../mock/fixtures'

const clone = (value) => structuredClone(value)

// Mock data stays behind the same boundary that future Spring API clients will implement.
export const finguardApi = {
  async createAgentRun() { return clone(agentRunFixture) },
  async getPermissionComparison() { return clone(permissionFixture) },
  async getAuditEvents() { return clone(auditEventsFixture) },
}
