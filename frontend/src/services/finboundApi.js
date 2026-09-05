import { agentExecutionFixtures, auditEventsFixture, bankWorkCatalogFixture } from '../mock/fixtures'

const clone = (value) => structuredClone(value)
const eventOutcome = (event) => event.auditStatus === 'ERROR' ? 'ERROR' : event.decision
const uniqueValues = (events, key) => [...new Set(events.map((event) => event[key]).filter(Boolean))]
const DEFAULT_TIMEOUT_MS = 8_000
const DEFAULT_EXECUTION_POLL_INTERVAL_MS = 250
const DEFAULT_EXECUTION_POLL_ATTEMPTS = 20
const EXECUTION_STATUSES = new Set(['RUNNING', 'COMPLETED', 'FAILED'])
const EXECUTION_TOOL_LABELS = {
  CREDIT_SCORE_READ: '신용정보 확인',
  INCOME_READ: '소득자료 확인',
  DEBT_READ: '부채자료 확인',
}

const initialMode = import.meta.env.VITE_FINBOUND_API_MODE === 'real' ? 'real' : 'mock'
const runtime = {
  mode: initialMode,
  baseUrl: (import.meta.env.VITE_FINBOUND_API_BASE_URL ?? '').replace(/\/$/, ''),
  credential: null,
  fetchImpl: (...args) => globalThis.fetch(...args),
  timeoutMs: DEFAULT_TIMEOUT_MS,
  executionPollIntervalMs: DEFAULT_EXECUTION_POLL_INTERVAL_MS,
  executionPollAttempts: DEFAULT_EXECUTION_POLL_ATTEMPTS,
  sleepImpl: (milliseconds) => new Promise((resolve) => setTimeout(resolve, milliseconds)),
}

export class FinboundApiError extends Error {
  constructor(message, { code = 'FRONTEND_API_ERROR', status = 0, cause, executionContext = null } = {}) {
    super(message, { cause })
    this.name = 'FinboundApiError'
    this.code = code
    this.status = status
    this.executionContext = executionContext
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
    let payload = null
    if (text) {
      try {
        payload = JSON.parse(text)
      } catch {
        if (response.ok) {
          throw new FinboundApiError('Core API returned an invalid JSON response', {
            code: 'CORE_API_INVALID_RESPONSE',
            status: response.status,
          })
        }
      }
    }
    if (!response.ok) {
      throw new FinboundApiError(payload?.detail ?? `Core API request failed (${response.status})`, {
        code: payload?.reasonCode ?? `CORE_API_HTTP_${response.status}`,
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
    promptRiskLevel: raw.promptRiskLevel ?? null,
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
  const supported = ['period', 'agentId', 'caseId', 'consumerId', 'tool', 'outcome', 'severity', 'reasonCode']
  supported.forEach((key) => {
    const value = filters[key]
    if (value && value !== 'ALL') query.set(key, value)
  })
  if (filters.riskOnly) query.set('riskOnly', 'true')
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

function executionDescription(attempt) {
  if (attempt.systemOutcome === 'ERROR') return '업무 시스템 처리 중 오류가 발생해 결과를 제공하지 못했습니다.'
  if (attempt.decision === 'BLOCK') return '현재 업무 범위를 벗어난 요청으로 금융시스템 조회 전에 차단했습니다.'
  if (attempt.decision === 'ALLOW' && attempt.systemOutcome === 'COMPLETED') return '현재 업무 범위 안에서 자료 확인을 완료했습니다.'
  return '실행 결과의 상세 설명이 제공되지 않았습니다.'
}

function mapExecutionAttempt(raw = {}) {
  const tool = raw.requestedTool ?? raw.tool ?? 'UNKNOWN'
  const attempt = {
    requestId: raw.requestId ?? '미제공',
    decision: raw.decision,
    systemOutcome: raw.systemOutcome,
    targetConsumerId: raw.targetConsumerId ?? '미제공',
    requestedData: Array.isArray(raw.requestedData) ? raw.requestedData : [],
    reasonCodes: Array.isArray(raw.reasonCodes) ? raw.reasonCodes : [],
    downstreamReached: typeof raw.downstreamReached === 'boolean' ? raw.downstreamReached : null,
    responseReleased: typeof raw.responseReleased === 'boolean' ? raw.responseReleased : null,
    scopeStatus: mapScopeStatus(raw.scopeStatus),
    tool,
    label: EXECUTION_TOOL_LABELS[tool] ?? tool,
  }
  return { ...attempt, description: executionDescription(attempt) }
}

function validateExecution(execution, agentRunId) {
  if (!execution || execution.agentRunId !== agentRunId || !EXECUTION_STATUSES.has(execution.status)) {
    throw new FinboundApiError('Core API returned an invalid Agent execution response', {
      code: 'CORE_API_INVALID_RESPONSE',
    })
  }
  if (execution.attempts !== undefined && !Array.isArray(execution.attempts)) {
    throw new FinboundApiError('Core API returned invalid Agent execution attempts', {
      code: 'CORE_API_INVALID_RESPONSE',
    })
  }
  if (execution.status === 'COMPLETED' && (!execution.attempts || execution.attempts.length === 0)) {
    throw new FinboundApiError('Completed Agent execution did not include an execution attempt', {
      code: 'CORE_API_INVALID_RESPONSE',
    })
  }
  if ((execution.attempts ?? []).some((attempt) => !isContractualAttempt(attempt))) {
    throw new FinboundApiError('Agent execution attempt did not preserve decision and system outcome', {
      code: 'CORE_API_INVALID_RESPONSE',
    })
  }
  return execution
}

/**
 * systemOutcome 은 언제나 있어야 하지만 decision 은 그렇지 않다.
 *
 * 정책 판정에 닿기 전에 시스템 장애로 차단된 실행은 decision 을 생략한다.
 * contracts/audit/execution-outcome.schema.json 이 systemOutcome=COMPLETED 일 때만
 * decision 을 요구하며, docs/06 §11 이 "시스템 장애는 Decision Enum 이 아니라
 * Audit/System Outcome 으로 표현한다"고 정했다.
 *
 * decision 을 늘 요구하면 Core·AI·OPA 장애로 막힌 실행을 통째로 버리게 된다.
 * 그러면 담당자는 "왜 막혔는지"가 아니라 "확인할 수 없습니다"만 본다 —
 * 기록이 가장 필요한 순간에 화면에서 사라진다.
 *
 * ERROR 가 decision 을 가질 수도 있다. Downstream 실패는 ALLOW + ERROR 다
 * (execution-outcome.error.valid.json). 그래서 부재를 허용하되 값이 오면 검사한다.
 *
 * 다만 BLOCK + ERROR 는 받지 않는다. 스키마가 표현할 수 없는 상태다 —
 * ERROR 절은 success 를 필수로 요구하고 BLOCK 절은 success 의 존재 자체를 금지한다
 * (execution-outcome.schema.json 의 두 조건부 절). 어느 값을 넣어도 통과하지 못한다.
 */
function isContractualAttempt(attempt) {
  if (!['COMPLETED', 'ERROR'].includes(attempt?.systemOutcome)) return false
  if (attempt.systemOutcome === 'ERROR') {
    return attempt.decision === undefined || attempt.decision === 'ALLOW'
  }
  return ['ALLOW', 'BLOCK'].includes(attempt.decision)
}

async function getAgentExecution(agentRunId) {
  const pathId = encodeURIComponent(agentRunId)
  return validateExecution(
    await coreRequest(`/api/v1/agent-runs/${pathId}/execution`),
    agentRunId,
  )
}

async function waitForAgentExecution(agentRunId) {
  let execution
  for (let attempt = 0; attempt < runtime.executionPollAttempts; attempt += 1) {
    execution = await getAgentExecution(agentRunId)
    if (execution.status !== 'RUNNING') return execution
    if (attempt + 1 < runtime.executionPollAttempts) {
      await runtime.sleepImpl(runtime.executionPollIntervalMs)
    }
  }
  return execution
}

function mapAgentExecution(agentRun, permission, execution) {
  const attempts = (execution.attempts ?? []).map(mapExecutionAttempt)
  const allowedCount = attempts.filter((attempt) => attempt.decision === 'ALLOW' && attempt.systemOutcome !== 'ERROR').length
  const blockedCount = attempts.filter((attempt) => attempt.decision === 'BLOCK').length
  const status = execution.status === 'FAILED' ? 'ERROR' : execution.status
  const errorCount = Math.max(
    attempts.filter((attempt) => attempt.systemOutcome === 'ERROR').length,
    status === 'ERROR' ? 1 : 0,
  )
  const executionReasonCodes = Array.isArray(execution.reasonCodes) ? execution.reasonCodes : []

  if (status === 'RUNNING') {
    return {
      status,
      title: 'AI 업무를 실행하고 있습니다',
      message: 'Core가 Agent를 호출했으며 실행 결과를 기다리고 있습니다.',
      resultHeading: '현재 업무 권한이 준비되었습니다',
      resultItems: mapPermissionSummary(permission),
      nextAction: '잠시 후 업무 기록에서 최신 실행 상태를 확인해 주세요.',
      attempts,
      agentRun,
      permission,
    }
  }

  return {
    status,
    title: status === 'ERROR' || errorCount ? 'AI 업무 처리 중 오류가 발생했습니다' : 'AI 업무 처리가 완료되었습니다',
    message: status === 'ERROR' || errorCount
      ? '정상 완료로 처리하지 않았습니다. 아래 실행 사유와 업무 기록을 확인해 주세요.'
      : 'Core Public API에서 확인한 Agent 실행 결과입니다.',
    resultHeading: 'Agent 실행 결과',
    resultItems: [
      `정상 확인 ${allowedCount}건`,
      `안전 차단 ${blockedCount}건`,
      `처리 오류 ${errorCount}건`,
      ...(executionReasonCodes.length ? [`오류 사유 ${executionReasonCodes.join(' · ')}`] : []),
    ],
    nextAction: status === 'ERROR' || errorCount
      ? '업무 기록에서 오류 사유를 확인한 뒤 재처리해 주세요.'
      : '실행 결과를 검토하고 다음 심사 업무를 진행해 주세요.',
    attempts,
    agentRun,
    permission,
  }
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

    let agentRun = null
    let permission = null
    try {
      agentRun = await coreRequest('/api/v1/agent-runs', {
        method: 'POST',
        body: {
          employeeId: work.employee.id,
          consumerId: work.case.consumerId,
          taskType: work.case.taskLabel,
          inputText: work.employeeRequest.title,
        },
      })
      permission = await coreRequest(`/api/v1/agent-runs/${encodeURIComponent(agentRun.agentRunId)}/permission-comparison`)
      const execution = await waitForAgentExecution(agentRun.agentRunId)

      return mapAgentExecution(agentRun, permission, execution)
    } catch (error) {
      if (!agentRun) throw error
      throw new FinboundApiError(error.message, {
        code: error.code,
        status: error.status,
        cause: error,
        executionContext: { agentRun, permission },
      })
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

export function configureFinboundApi({
  mode,
  baseUrl,
  credential,
  fetchImpl,
  timeoutMs,
  executionPollIntervalMs,
  executionPollAttempts,
  sleepImpl,
} = {}) {
  if (mode !== undefined) runtime.mode = mode === 'real' ? 'real' : 'mock'
  if (baseUrl !== undefined) runtime.baseUrl = baseUrl.replace(/\/$/, '')
  if (credential !== undefined) runtime.credential = credential || null
  if (fetchImpl !== undefined) runtime.fetchImpl = fetchImpl
  if (timeoutMs !== undefined) runtime.timeoutMs = timeoutMs
  if (executionPollIntervalMs !== undefined) runtime.executionPollIntervalMs = Math.max(0, executionPollIntervalMs)
  if (executionPollAttempts !== undefined) runtime.executionPollAttempts = Math.max(1, executionPollAttempts)
  if (sleepImpl !== undefined) runtime.sleepImpl = sleepImpl
}

export function resetFinboundApi() {
  runtime.mode = initialMode
  runtime.baseUrl = (import.meta.env.VITE_FINBOUND_API_BASE_URL ?? '').replace(/\/$/, '')
  runtime.credential = null
  runtime.fetchImpl = (...args) => globalThis.fetch(...args)
  runtime.timeoutMs = DEFAULT_TIMEOUT_MS
  runtime.executionPollIntervalMs = DEFAULT_EXECUTION_POLL_INTERVAL_MS
  runtime.executionPollAttempts = DEFAULT_EXECUTION_POLL_ATTEMPTS
  runtime.sleepImpl = (milliseconds) => new Promise((resolve) => setTimeout(resolve, milliseconds))
}

export const finboundApi = {
  isRealMode: () => runtime.mode === 'real',
  hasCredential: () => Boolean(runtime.credential),
  capabilities: () => ({
    severityFilter: true,
    riskOnlyFilter: true,
  }),
  setCredential(credential) { runtime.credential = credential || null },
  clearCredential() { runtime.credential = null },
  getBankWorkCatalog: (...args) => activeApi().getBankWorkCatalog(...args),
  executeAgentTask: (...args) => activeApi().executeAgentTask(...args),
  getAuditEvents: (...args) => activeApi().getAuditEvents(...args),
  getAuditEvent: (...args) => activeApi().getAuditEvent(...args),
  getDashboardSummary: (...args) => activeApi().getDashboardSummary(...args),
}
