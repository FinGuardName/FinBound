import { afterEach, describe, expect, it, vi } from 'vitest'

import {
  configureFinboundApi,
  finboundApi,
  FinboundApiError,
  mapAuditEvent,
  resetFinboundApi,
} from './finboundApi'

function jsonResponse(body, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    text: async () => JSON.stringify(body),
  }
}

afterEach(() => {
  resetFinboundApi()
  vi.restoreAllMocks()
})

describe('real Core API adapter', () => {
  it('creates an AgentRun with an in-memory credential and reads permission comparison', async () => {
    const fetchImpl = vi.fn()
      .mockResolvedValueOnce(jsonResponse({
        agentRunId: 'RUN-REAL-1',
        agentId: 'LOAN-AGENT-01',
        employeeId: 'EMP-101',
        caseId: 'CASE-REAL-1',
        passportId: 'PASS-REAL-1',
        inputRefs: ['INPUT-REAL-1'],
        status: 'RUNNING',
        startedAt: '2026-09-01T12:00:00+09:00',
      }))
      .mockResolvedValueOnce(jsonResponse({
        agentRunId: 'RUN-REAL-1',
        agentEffectivePermission: {
          allowedTools: ['CREDIT_SCORE_READ'],
          allowedData: ['CREDIT_SCORE'],
        },
        withheldTools: ['INCOME_READ'],
      }))

    configureFinboundApi({
      mode: 'real',
      baseUrl: 'http://localhost:8080/',
      credential: 'operator-test-credential',
      fetchImpl,
    })

    const result = await finboundApi.executeAgentTask({ workId: 'NEW_LOAN' })

    expect(fetchImpl).toHaveBeenCalledTimes(2)
    expect(fetchImpl.mock.calls[0][0]).toBe('http://localhost:8080/api/v1/agent-runs')
    expect(fetchImpl.mock.calls[0][1].headers.Authorization).toBe('Bearer operator-test-credential')
    expect(JSON.parse(fetchImpl.mock.calls[0][1].body)).toEqual({
      employeeId: 'EMP-101',
      consumerId: 'CUST-1001',
      taskType: 'LOAN_REVIEW',
      inputText: '현재 고객의 신규 대출 심사자료 확인',
    })
    expect(fetchImpl.mock.calls[1][0]).toContain('/api/v1/agent-runs/RUN-REAL-1/permission-comparison')
    expect(result.status).toBe('RUNNING')
    expect(result.attempts).toEqual([])
    expect(result.resultItems).toContain('허용 업무 1개')
  })

  it('maps official audit fields and sends only supported server filters', async () => {
    const fetchImpl = vi.fn().mockResolvedValue(jsonResponse({
      items: [{
        auditEventId: 'AUD-REAL-1',
        status: 'ERROR',
        systemOutcome: 'ERROR',
        promptRiskEvaluationStatus: 'EVALUATED',
        behaviorFeatureVersion: 'behavior-features-2',
        reasonCodes: ['DOWNSTREAM_TIMEOUT'],
      }],
      page: 2,
      pageSize: 5,
      totalItems: 8,
      totalPages: 2,
    }))
    configureFinboundApi({ mode: 'real', credential: 'viewer', fetchImpl })

    const result = await finboundApi.getAuditEvents({
      filters: {
        period: '24H',
        outcome: 'ERROR',
        severity: 'HIGH',
        riskOnly: true,
      },
      page: 2,
      pageSize: 5,
    })

    const url = new URL(fetchImpl.mock.calls[0][0], 'http://frontend.local')
    expect(url.searchParams.get('period')).toBe('24H')
    expect(url.searchParams.get('outcome')).toBe('ERROR')
    expect(url.searchParams.get('page')).toBe('2')
    expect(url.searchParams.has('severity')).toBe(false)
    expect(url.searchParams.has('riskOnly')).toBe(false)
    expect(result.items[0]).toMatchObject({
      auditStatus: 'ERROR',
      promptEvaluationStatus: 'EVALUATED',
      featureVersion: 'behavior-features-2',
    })
  })

  it('does not call Core without a credential', async () => {
    const fetchImpl = vi.fn()
    configureFinboundApi({ mode: 'real', fetchImpl })

    await expect(finboundApi.getDashboardSummary()).rejects.toMatchObject({
      name: 'FinboundApiError',
      code: 'CORE_API_CREDENTIAL_REQUIRED',
    })
    expect(fetchImpl).not.toHaveBeenCalled()
  })

  it('preserves fail-closed HTTP error codes', async () => {
    const fetchImpl = vi.fn().mockResolvedValue(jsonResponse({
      errorCode: 'CORE_API_ROLE_FORBIDDEN',
      message: 'Viewer cannot create AgentRun',
    }, 403))
    configureFinboundApi({ mode: 'real', credential: 'viewer', fetchImpl })

    await expect(finboundApi.executeAgentTask({ workId: 'NEW_LOAN' })).rejects.toEqual(
      expect.objectContaining({
        name: 'FinboundApiError',
        code: 'CORE_API_ROLE_FORBIDDEN',
        status: 403,
      }),
    )
  })

  it('never derives unavailable backend risk fields as safe values', () => {
    const mapped = mapAuditEvent({ auditEventId: 'AUD-1', status: 'PROCESSING' })

    expect(mapped.severity).toBe('UNKNOWN')
    expect(mapped.behaviorRiskLevel).toBe('UNKNOWN')
    expect(mapped.riskFlagged).toBeNull()
    expect(mapped.promptInjectionDetected).toBeNull()
    expect(mapped.systemOutcome).toBeNull()
  })

  it('uses a dedicated typed error for adapter failures', () => {
    expect(new FinboundApiError('failed')).toBeInstanceOf(Error)
  })
})
