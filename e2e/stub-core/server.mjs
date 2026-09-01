import { createServer } from 'node:http'
import { timingSafeEqual } from 'node:crypto'

const port = 8080
const expectedCredential = process.env.E2E_OPERATOR_CREDENTIAL ?? ''

const scopeStatus = {
  employeeAuthority: 'OK',
  permissionTemplate: 'OK',
  caseStatus: 'OK',
  mandate: 'OK',
  passportStatus: 'OK',
  agentBinding: 'OK',
  customerScope: 'OK',
  toolScope: 'OK',
  dataScope: 'OK',
}

const auditEvent = {
  auditEventId: 'AUD-E2E-001',
  requestId: 'REQ-E2E-001',
  agentId: 'credit-review-agent',
  agentRunId: 'RUN-E2E-001',
  caseId: 'CASE-2026-001',
  targetConsumerId: 'CUST-1001',
  requestedTool: 'DEBT_READ',
  requestedData: ['DEBT'],
  requestedAt: '2026-09-01T12:00:00Z',
  status: 'COMPLETED',
  systemOutcome: 'COMPLETED',
  decision: 'BLOCK',
  downstreamReached: false,
  responseReleased: false,
  reasonCodes: ['BEHAVIOR_ANOMALY'],
  scopeStatus,
  promptRiskEvaluationStatus: 'EVALUATED',
  promptRisk: 0.21,
  promptInjectionDetected: false,
  promptModelVersion: 'prompt-guard-e2e',
  behaviorRisk: 0.97,
  behaviorRiskLevel: 'CRITICAL',
  behaviorAnomalyDetected: true,
  behaviorFeatureVersion: 'behavior-features-e2e',
  behaviorModelVersion: 'iforest-e2e',
  policyVersion: 'finguard-authz-e2e',
  promptText: 'E2E_RAW_PROMPT_MUST_NOT_RENDER',
  financialPayload: 'E2E_FINANCIAL_PAYLOAD_MUST_NOT_RENDER',
}

function sendJson(response, status, body) {
  response.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Cache-Control': 'no-store',
  })
  response.end(JSON.stringify(body))
}

function credentialMatches(header) {
  const supplied = header?.startsWith('Bearer ') ? header.slice(7) : ''
  const expected = Buffer.from(expectedCredential)
  const actual = Buffer.from(supplied)
  return expected.length > 0 && expected.length === actual.length && timingSafeEqual(expected, actual)
}

async function readBody(request) {
  const chunks = []
  for await (const chunk of request) chunks.push(chunk)
  if (!chunks.length) return null
  return JSON.parse(Buffer.concat(chunks).toString('utf8'))
}

const server = createServer(async (request, response) => {
  const url = new URL(request.url ?? '/', 'http://stub-core')

  if (request.method === 'GET' && url.pathname === '/health') {
    return sendJson(response, 200, { status: 'UP' })
  }

  if (!credentialMatches(request.headers.authorization)) {
    return sendJson(response, 401, { errorCode: 'CORE_API_CREDENTIAL_INVALID', message: 'Credential is invalid' })
  }

  try {
    if (request.method === 'POST' && url.pathname === '/api/v1/agent-runs') {
      const body = await readBody(request)
      if (!body?.employeeId || !body?.consumerId || !body?.taskType || !body?.inputText) {
        return sendJson(response, 400, { errorCode: 'REQUEST_INVALID', message: 'Required fields are missing' })
      }
      return sendJson(response, 201, {
        agentRunId: 'RUN-E2E-001',
        passportId: 'PASSPORT-E2E-001',
        caseId: 'CASE-2026-001',
        status: 'RUNNING',
      })
    }

    if (request.method === 'GET' && url.pathname === '/api/v1/agent-runs/RUN-E2E-001/permission-comparison') {
      return sendJson(response, 200, {
        agentEffectivePermission: {
          allowedTools: ['CREDIT_SCORE_READ', 'INCOME_READ'],
          allowedData: ['CREDIT_SCORE', 'INCOME'],
        },
        withheldTools: ['DEBT_READ'],
      })
    }

    if (request.method === 'GET' && url.pathname === '/api/v1/audit-events') {
      return sendJson(response, 200, {
        items: [auditEvent],
        page: 1,
        pageSize: 5,
        totalItems: 1,
        totalPages: 1,
      })
    }

    if (request.method === 'GET' && url.pathname === `/api/v1/audit-events/${auditEvent.auditEventId}`) {
      return sendJson(response, 200, auditEvent)
    }

    if (request.method === 'GET' && url.pathname === '/api/v1/dashboard/summary') {
      return sendJson(response, 200, { total: 1, allow: 0, block: 1, error: 0 })
    }

    return sendJson(response, 404, { errorCode: 'NOT_FOUND', message: 'Route is not implemented by the E2E stub' })
  } catch {
    return sendJson(response, 400, { errorCode: 'REQUEST_INVALID', message: 'Request body is invalid' })
  }
})

server.listen(port, '0.0.0.0')

for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, () => server.close(() => process.exit(0)))
}
