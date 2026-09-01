import { agentExecutionFixtures, auditEventsFixture, bankWorkCatalogFixture } from '../mock/fixtures'

const clone = (value) => structuredClone(value)
const eventOutcome = (event) => event.auditStatus === 'ERROR' ? 'ERROR' : event.decision
const uniqueValues = (events, key) => [...new Set(events.map((event) => event[key]).filter(Boolean))]
const DEFAULT_TIMEOUT_MS = 8_000

const initialMode = import.meta.env.VITE_FINBOUND_API_MODE === 'real' ? 'real' : 'mock'
const runtime = {
  mode: initialMode,
  baseUrl: (import.meta.env.VITE_FINBOUND_API_BASE_URL ?? '').replace(/\/$/, ''),
  credential: null,
  fetchImpl: (...args) => globalThis.fetch(...args),
  timeoutMs: DEFAULT_TIMEOUT_MS,
}

export class FinboundApiError extends Error {
  constructor(message, { code = 'FRONTEND_API_ERROR', status = 0, cause } = {}) {
    super(message, { cause })
    this.name = 'FinboundApiError'
    this.code = code
    this.status = status
  }
}

function buildFilterOptions(events) {
  return {
    agentIds: uniqueValues(events, 'agentId'),
    caseIds: uniqueValues(events, 'caseId'),
    consumerIds: uniqueValues(events, 'targetConsumerId'),
    tools: uniqueValues(events, 'requestedTool'),
    reasonCodes: [...new Set(events.flatMap((event) => event.reasonCodes ?? []))],
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

function requireCredential() {
  if (!runtime.credential) {
    throw new FinboundApiError('Core API credential is required', { code: 'CORE_API_CREDENTIAL_REQUIRED' })
  }
}

function requestId() {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID()
  const bytes = new Uint8Array(16)
  globalThis.crypto?.getRandomValues?.(bytes)
  bytes[6] = (bytes[6] & 0x0f) | 0x40
  bytes[8] = (bytes[8] & 0x3f) | 0x80
  const hex = [...bytes].map((value) => value.toString(16).padStart(2, '0')).join('')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}

async function coreRequest(path, { method = 'GET', body } = {}) {
  requireCredential()
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), runtime.timeoutMs)

  try {
    const response = await runtime.fetchImpl(`${runtime.baseUrl}${path}`, {
      method,
      headers: {
        Accept: 'application/json',
        Authorization: `Bearer ${runtime.credential}`,
        'Content-Type': 'application/json',
        'X-Request-Id': requestId(),
      },
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: controller.signal,
    })
    const text = await response.text()
    const payload = text ? JSON.parse(text) : null
    if (!response.ok) {
      throw new FinboundApiError(payload?.message ?? 'Core API request failed', {
        code: payload?.errorCode ?? `CORE_API_HTTP_${response.status}`,
        status: response.status,
      })
    }
    return payload
  } catch (error) {
    if (error instanceof FinboundApiError) throw error
    if (error?.name === 'AbortError') {
      throw new FinboundApiError('Core API request timed out', { code: 'CORE_API_TIMEOUT', cause: error })
    }
    throw new FinboundApiError('Core API is unavailable', { code: 'CORE_API_UNAVAILABLE', cause: error })
  } finally {
    clearTimeout(timer)
  }
}

function mapScopeStatus(scopeStatus) {
  return scopeStatus ?? {
    employeeAuthority: 'UNKNOWN',
    permissionTemplate: 'UNKNOWN',
    caseStatus: 'UNKNOWN',
    mandate: 'UNKNOWN',
    passportStatus: 'UNKNOWN',
    agentBinding: 'UNKNOWN',
    customerScope: 'UNKNOWN',
    toolScope: 'UNKNOWN',
    dataScope: 'UNKNOWN',
  }
}

export function mapAuditEvent(raw) {
  return {
    ...raw,
    auditStatus: raw.status,
    systemOutcome: raw.systemOutcome ?? (raw.status === 'PROCESSING' ? null : raw.status),
    promptEvaluationStatus: raw.promptRiskEvaluationStatus ?? 'NOT_EVALUATED',
    featureVersion: raw.behaviorFeatureVersion ?? null,
    behaviorRiskLevel: raw.behaviorRiskLevel ?? 'UNKNOWN',
    severity: raw.severity ?? 'UNKNOWN',
    riskFlagged: raw.riskFlagged ?? null,
    promptInjectionDetected: raw.promptInjectionDetected ?? null,
    behaviorAnomalyDetected: raw.behaviorAnomalyDetected ?? null,
    requestedData: raw.requestedData ?? [],
    reasonCodes: raw.reasonCodes ?? [],
    scopeStatus: mapScopeStatus(raw.scopeStatus),
  }
}

function queryString({ filters = {}, page = 1, pageSize = 5 }) {
  const query = new URLSearchParams({ page: String(page), pageSize: String(pageSize) })
  const supported = ['period', 'agentId', 'caseId', 'consumerId', 'tool', 'outcome', 'reasonCode']
  supported.forEach((key) => {
    const value = filters[key]
    if (value && value !== 'ALL') query.set(key, value)
  })
  return query.toString()
}

function mapPermissionSummary(permission) {
  const effective = permission.agentEffectivePermission ?? { allowedTools: [], allowedData: [] }
  return [
    `허용 업무 ${effective.allowedTools.length}개`,
    `허용 자료 ${effective.allowedData.length}개`,
    `권한 제외 업무 ${permission.withheldTools?.length ?? 0}개`,
  ]
}

const mockApi = {
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
  async getAuditEvent(auditEventId) {
    const event = auditEventsFixture.find((candidate) => candidate.auditEventId === auditEventId)
    if (!event) throw new FinboundApiError('Audit event not found', { code: 'AUDIT_EVENT_NOT_FOUND', status: 404 })
    return clone(event)
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

const realApi = {
  async getBankWorkCatalog() { return clone(bankWorkCatalogFixture) },
  async executeAgentTask({ workId }) {
    const work = bankWorkCatalogFixture.find((candidate) => candidate.id === workId)
    if (!work) throw new FinboundApiError('Unsupported Agent task', { code: 'AGENT_TASK_UNSUPPORTED' })

    const agentRun = await coreRequest('/api/v1/agent-runs', {
      method: 'POST',
      body: {
        employeeId: work.employee.id,
        consumerId: work.case.consumerId,
        taskType: work.case.taskLabel,
        inputText: work.employeeRequest.title,
      },
    })
    const permission = await coreRequest(`/api/v1/agent-runs/${encodeURIComponent(agentRun.agentRunId)}/permission-comparison`)

    return {
      status: agentRun.status,
      title: 'AI 업무 실행이 등록되었습니다',
      message: 'Core가 현재 업무의 AgentRun과 최소 권한을 발급했습니다.',
      resultHeading: '현재 업무 권한이 준비되었습니다',
      resultItems: mapPermissionSummary(permission),
      nextAction: 'Agent 실행 결과는 감사 현황에서 확인해 주세요.',
      attempts: [],
      agentRun,
      permission,
    }
  },
  async getAuditEvents(options = {}) {
    const page = await coreRequest(`/api/v1/audit-events?${queryString(options)}`)
    const items = (page.items ?? []).map(mapAuditEvent)
    return {
      ...page,
      items,
      filterOptions: buildFilterOptions(items),
    }
  },
  async getAuditEvent(auditEventId) {
    return mapAuditEvent(await coreRequest(`/api/v1/audit-events/${encodeURIComponent(auditEventId)}`))
  },
  async getDashboardSummary() {
    return coreRequest('/api/v1/dashboard/summary')
  },
}

function activeApi() {
  return runtime.mode === 'real' ? realApi : mockApi
}

export function configureFinboundApi({ mode, baseUrl, credential, fetchImpl, timeoutMs } = {}) {
  if (mode !== undefined) runtime.mode = mode === 'real' ? 'real' : 'mock'
  if (baseUrl !== undefined) runtime.baseUrl = baseUrl.replace(/\/$/, '')
  if (credential !== undefined) runtime.credential = credential || null
  if (fetchImpl !== undefined) runtime.fetchImpl = fetchImpl
  if (timeoutMs !== undefined) runtime.timeoutMs = timeoutMs
}

export function resetFinboundApi() {
  runtime.mode = initialMode
  runtime.baseUrl = (import.meta.env.VITE_FINBOUND_API_BASE_URL ?? '').replace(/\/$/, '')
  runtime.credential = null
  runtime.fetchImpl = (...args) => globalThis.fetch(...args)
  runtime.timeoutMs = DEFAULT_TIMEOUT_MS
}

export const finboundApi = {
  isRealMode: () => runtime.mode === 'real',
  hasCredential: () => Boolean(runtime.credential),
  capabilities: () => ({
    severityFilter: runtime.mode === 'mock',
    riskOnlyFilter: runtime.mode === 'mock',
  }),
  setCredential(credential) { runtime.credential = credential || null },
  clearCredential() { runtime.credential = null },
  getBankWorkCatalog: (...args) => activeApi().getBankWorkCatalog(...args),
  executeAgentTask: (...args) => activeApi().executeAgentTask(...args),
  getAuditEvents: (...args) => activeApi().getAuditEvents(...args),
  getAuditEvent: (...args) => activeApi().getAuditEvent(...args),
  getDashboardSummary: (...args) => activeApi().getDashboardSummary(...args),
}
