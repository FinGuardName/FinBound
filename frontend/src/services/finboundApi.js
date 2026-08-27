import { agentExecutionFixtures, auditEventsFixture, bankWorkCatalogFixture } from '../mock/fixtures'

const clone = (value) => structuredClone(value)
const eventOutcome = (event) => event.auditStatus === 'ERROR' ? 'ERROR' : event.decision
const uniqueValues = (events, key) => [...new Set(events.map((event) => event[key]))]

function buildFilterOptions(events) {
  return {
    agentIds: uniqueValues(events, 'agentId'),
    caseIds: uniqueValues(events, 'caseId'),
    consumerIds: uniqueValues(events, 'targetConsumerId'),
    tools: uniqueValues(events, 'requestedTool'),
    reasonCodes: [...new Set(events.flatMap((event) => event.reasonCodes))],
  }
}

function filterAuditEvents(events, filters) {
  const latestEventTime = Math.max(...events.map((event) => new Date(event.requestedAt).getTime()))
  const isInsidePeriod = (event) => {
    if (!filters.period || filters.period === 'ALL') return true
    const periodMs = filters.period === '30M' ? 30 * 60 * 1000 : 24 * 60 * 60 * 1000
    return latestEventTime - new Date(event.requestedAt).getTime() <= periodMs
  }

  return events.filter((event) => (
    isInsidePeriod(event)
    && (!filters.agentId || filters.agentId === 'ALL' || event.agentId === filters.agentId)
    && (!filters.caseId || filters.caseId === 'ALL' || event.caseId === filters.caseId)
    && (!filters.consumerId || filters.consumerId === 'ALL' || event.targetConsumerId === filters.consumerId)
    && (!filters.tool || filters.tool === 'ALL' || event.requestedTool === filters.tool)
    && (!filters.outcome || filters.outcome === 'ALL' || eventOutcome(event) === filters.outcome)
    && (!filters.severity || filters.severity === 'ALL' || event.severity === filters.severity)
    && (!filters.reasonCode || filters.reasonCode === 'ALL' || event.reasonCodes.includes(filters.reasonCode))
    && (!filters.riskOnly || event.riskFlagged || ['HIGH', 'CRITICAL'].includes(event.severity))
  ))
}

// Mock data stays behind the same boundary that the future Spring Core adapter will implement.
// List filters and pagination are passed to this boundary instead of loading an unbounded audit
// collection in the browser. The browser never calls OPA, AI, or downstream tools directly.
export const finboundApi = {
  async getBankWorkCatalog() { return clone(bankWorkCatalogFixture) },
  async executeAgentTask({ workId }) {
    const execution = agentExecutionFixtures[workId]
    if (!execution) throw new Error('Unsupported Agent task')
    return clone(execution)
  },
  async getAuditEvents({ filters = {}, page = 1, pageSize = 5 } = {}) {
    const allEvents = clone(auditEventsFixture)
      .sort((left, right) => new Date(right.requestedAt) - new Date(left.requestedAt))
    const filtered = filterAuditEvents(allEvents, filters)
    const normalizedPageSize = Math.max(1, pageSize)
    const totalPages = Math.max(1, Math.ceil(filtered.length / normalizedPageSize))
    const normalizedPage = Math.min(Math.max(1, page), totalPages)
    const offset = (normalizedPage - 1) * normalizedPageSize

    return {
      items: filtered.slice(offset, offset + normalizedPageSize),
      page: normalizedPage,
      pageSize: normalizedPageSize,
      totalItems: filtered.length,
      totalPages,
      filterOptions: buildFilterOptions(allEvents),
    }
  },
  async getDashboardSummary() {
    const events = clone(auditEventsFixture)
    return {
      total: events.length,
      allow: events.filter((event) => eventOutcome(event) === 'ALLOW').length,
      block: events.filter((event) => eventOutcome(event) === 'BLOCK').length,
      error: events.filter((event) => eventOutcome(event) === 'ERROR').length,
    }
  },
}
