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
  it('creates an AgentRun and reads its result only through the Core Public execution API', async () => {
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
      .mockResolvedValueOnce(jsonResponse({
        agentRunId: 'RUN-REAL-1',
        status: 'COMPLETED',
        attempts: [{
          requestId: 'REQ-REAL-1',
          requestedTool: 'CREDIT_SCORE_READ',
          targetConsumerId: 'CUST-1001',
          requestedData: ['CREDIT_SCORE'],
          decision: 'ALLOW',
          systemOutcome: 'COMPLETED',
          reasonCodes: [],
          downstreamReached: true,
          responseReleased: true,
          scopeStatus: { customerScope: 'OK' },
        }],
      }))

    configureFinboundApi({
      mode: 'real',
      baseUrl: 'http://localhost:8080/',
      credential: 'operator-test-credential',
      fetchImpl,
    })

    const result = await finboundApi.executeAgentTask({ workId: 'NEW_LOAN' })

    expect(fetchImpl).toHaveBeenCalledTimes(3)
    expect(fetchImpl.mock.calls[0][0]).toBe('http://localhost:8080/api/v1/agent-runs')
    expect(fetchImpl.mock.calls[0][1].headers.Authorization).toBe('Bearer operator-test-credential')
    expect(JSON.parse(fetchImpl.mock.calls[0][1].body)).toEqual({
      employeeId: 'EMP-101',
      consumerId: 'CUST-1001',
      taskType: 'LOAN_REVIEW',
      inputText: '현재 고객의 신규 대출 심사자료 확인',
    })
    expect(fetchImpl.mock.calls[1][0]).toContain('/api/v1/agent-runs/RUN-REAL-1/permission-comparison')
    expect(fetchImpl.mock.calls[2][0]).toBe('http://localhost:8080/api/v1/agent-runs/RUN-REAL-1/execution')
    expect(fetchImpl.mock.calls.every(([url]) => !url.includes('/internal/'))).toBe(true)
    expect(result.status).toBe('COMPLETED')
    expect(result.attempts[0]).toMatchObject({
      tool: 'CREDIT_SCORE_READ',
      decision: 'ALLOW',
      systemOutcome: 'COMPLETED',
      downstreamReached: true,
      responseReleased: true,
    })
    expect(result.resultItems).toContain('정상 확인 1건')
    expect(result.agentRun).toMatchObject({
      agentRunId: 'RUN-REAL-1',
      passportId: 'PASS-REAL-1',
    })
    expect(result.permission).toMatchObject({
      agentEffectivePermission: {
        allowedTools: ['CREDIT_SCORE_READ'],
        allowedData: ['CREDIT_SCORE'],
      },
      withheldTools: ['INCOME_READ'],
    })
  })

  it('polls a running Agent execution without calling an internal endpoint', async () => {
    const fetchImpl = vi.fn()
      .mockResolvedValueOnce(jsonResponse({ agentRunId: 'RUN-POLL-1', status: 'RUNNING' }))
      .mockResolvedValueOnce(jsonResponse({
        agentEffectivePermission: { allowedTools: [], allowedData: [] },
        withheldTools: [],
      }))
      .mockResolvedValueOnce(jsonResponse({ agentRunId: 'RUN-POLL-1', status: 'RUNNING', attempts: [] }))
      .mockResolvedValueOnce(jsonResponse({
        agentRunId: 'RUN-POLL-1',
        status: 'COMPLETED',
        attempts: [{
          requestId: 'REQ-POLL-1',
          tool: 'CREDIT_SCORE_READ',
          targetConsumerId: 'CUST-1001',
          requestedData: ['CREDIT_SCORE'],
          decision: 'BLOCK',
          systemOutcome: 'COMPLETED',
          reasonCodes: ['CASE_SCOPE_VIOLATION'],
          downstreamReached: false,
          responseReleased: false,
        }],
      }))
    const sleepImpl = vi.fn().mockResolvedValue(undefined)
    configureFinboundApi({
      mode: 'real',
      credential: 'operator',
      fetchImpl,
      executionPollAttempts: 2,
      sleepImpl,
    })

    const result = await finboundApi.executeAgentTask({ workId: 'NEW_LOAN' })

    expect(sleepImpl).toHaveBeenCalledTimes(1)
    expect(fetchImpl.mock.calls.filter(([url]) => url.endsWith('/execution'))).toHaveLength(2)
    expect(fetchImpl.mock.calls.every(([url]) => !url.includes('/internal/'))).toBe(true)
    expect(result.attempts[0]).toMatchObject({
      decision: 'BLOCK',
      systemOutcome: 'COMPLETED',
      downstreamReached: false,
      responseReleased: false,
    })
  })

  it('preserves verified AgentRun and permission context when execution lookup fails', async () => {
    const fetchImpl = vi.fn()
      .mockResolvedValueOnce(jsonResponse({
        agentRunId: 'RUN-PARTIAL-1',
        passportId: 'PASS-PARTIAL-1',
        status: 'RUNNING',
      }))
      .mockResolvedValueOnce(jsonResponse({
        agentEffectivePermission: {
          allowedTools: ['CREDIT_SCORE_READ'],
          allowedData: ['CREDIT_SCORE'],
        },
        withheldTools: ['INCOME_READ'],
      }))
      .mockRejectedValueOnce(new Error('execution endpoint unavailable'))
    configureFinboundApi({ mode: 'real', credential: 'operator', fetchImpl })

    await expect(finboundApi.executeAgentTask({ workId: 'NEW_LOAN' })).rejects.toMatchObject({
      code: 'CORE_API_UNAVAILABLE',
      executionContext: {
        agentRun: {
          agentRunId: 'RUN-PARTIAL-1',
          passportId: 'PASS-PARTIAL-1',
        },
        permission: {
          agentEffectivePermission: {
            allowedTools: ['CREDIT_SCORE_READ'],
            allowedData: ['CREDIT_SCORE'],
          },
          withheldTools: ['INCOME_READ'],
        },
      },
    })
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

  it('preserves application/problem+json reasonCode and detail', async () => {
    const fetchImpl = vi.fn().mockResolvedValue(jsonResponse({
      type: 'about:blank',
      title: 'Forbidden',
      status: 403,
      detail: 'Viewer cannot create AgentRun',
      reasonCode: 'CORE_API_ROLE_FORBIDDEN',
    }, 403))
    configureFinboundApi({ mode: 'real', credential: 'viewer', fetchImpl })

    await expect(finboundApi.executeAgentTask({ workId: 'NEW_LOAN' })).rejects.toEqual(
      expect.objectContaining({
        name: 'FinboundApiError',
        code: 'CORE_API_ROLE_FORBIDDEN',
        status: 403,
        message: 'Viewer cannot create AgentRun',
      }),
    )
  })

  it('preserves a credential-filter reasonCode even when detail is absent', async () => {
    const fetchImpl = vi.fn().mockResolvedValue(jsonResponse({
      reasonCode: 'CORE_API_CREDENTIAL_INVALID',
    }, 401))
    configureFinboundApi({ mode: 'real', credential: 'invalid', fetchImpl })

    await expect(finboundApi.getDashboardSummary()).rejects.toMatchObject({
      code: 'CORE_API_CREDENTIAL_INVALID',
      status: 401,
    })
  })

  it('preserves a validation problem reasonCode and detail', async () => {
    const fetchImpl = vi.fn().mockResolvedValue(jsonResponse({
      status: 422,
      detail: 'The request contains invalid fields',
      reasonCode: 'CORE_API_VALIDATION_FAILED',
      invalidFields: ['employeeId'],
    }, 422))
    configureFinboundApi({ mode: 'real', credential: 'operator', fetchImpl })

    await expect(finboundApi.executeAgentTask({ workId: 'NEW_LOAN' })).rejects.toMatchObject({
      code: 'CORE_API_VALIDATION_FAILED',
      status: 422,
      message: 'The request contains invalid fields',
    })
  })

  it('does not convert a non-JSON Core error into an unavailable-network error', async () => {
    const fetchImpl = vi.fn().mockResolvedValue({
      ok: false,
      status: 502,
      text: async () => '<html>bad gateway</html>',
    })
    configureFinboundApi({ mode: 'real', credential: 'operator', fetchImpl })

    await expect(finboundApi.getDashboardSummary()).rejects.toMatchObject({
      code: 'CORE_API_HTTP_502',
      status: 502,
    })
  })

  it('fails closed when a completed execution omits its result attempts', async () => {
    const fetchImpl = vi.fn()
      .mockResolvedValueOnce(jsonResponse({ agentRunId: 'RUN-BROKEN', status: 'RUNNING' }))
      .mockResolvedValueOnce(jsonResponse({
        agentEffectivePermission: { allowedTools: [], allowedData: [] },
        withheldTools: [],
      }))
      .mockResolvedValueOnce(jsonResponse({ agentRunId: 'RUN-BROKEN', status: 'COMPLETED', attempts: [] }))
    configureFinboundApi({ mode: 'real', credential: 'operator', fetchImpl })

    await expect(finboundApi.executeAgentTask({ workId: 'NEW_LOAN' })).rejects.toMatchObject({
      code: 'CORE_API_INVALID_RESPONSE',
    })
  })

  it('keeps a fail-closed attempt that carries no policy decision', async () => {
    // 정책 판정에 닿기 전 시스템 장애로 차단된 실행은 decision 을 생략한다.
    // contracts/audit/fixtures/execution-outcome.fail-closed.valid.json 이 그 모양이고,
    // execution-outcome.schema.json 은 systemOutcome=COMPLETED 일 때만 decision 을 요구한다.
    // 여기서 거부하면 Core·AI 장애로 막힌 실행이 화면에서 통째로 사라진다.
    const fetchImpl = vi.fn()
      .mockResolvedValueOnce(jsonResponse({ agentRunId: 'RUN-FAILCLOSED', status: 'RUNNING' }))
      .mockResolvedValueOnce(jsonResponse({
        agentEffectivePermission: { allowedTools: [], allowedData: [] },
        withheldTools: [],
      }))
      .mockResolvedValueOnce(jsonResponse({
        agentRunId: 'RUN-FAILCLOSED',
        status: 'COMPLETED',
        attempts: [{
          requestId: '00000000-0000-4000-8000-00000000f001',
          requestedTool: 'CREDIT_SCORE_READ',
          systemOutcome: 'ERROR',
          reasonCodes: ['CONTEXT_SERVICE_UNAVAILABLE'],
          downstreamReached: false,
          responseReleased: false,
          success: false,
          errorLocation: 'CORE',
        }],
      }))
    configureFinboundApi({ mode: 'real', credential: 'operator', fetchImpl })

    const execution = await finboundApi.executeAgentTask({ workId: 'NEW_LOAN' })

    expect(execution.attempts).toHaveLength(1)
    expect(execution.attempts[0].systemOutcome).toBe('ERROR')
    expect(execution.attempts[0].decision).toBeUndefined()
    expect(execution.attempts[0].reasonCodes).toContain('CONTEXT_SERVICE_UNAVAILABLE')
  })

  it('still rejects a completed attempt that omits its policy decision', async () => {
    // 부재를 허용하는 것은 ERROR 뿐이다. COMPLETED 는 판정이 끝났다는 뜻이므로
    // decision 이 없으면 계약 위반이다.
    const fetchImpl = vi.fn()
      .mockResolvedValueOnce(jsonResponse({ agentRunId: 'RUN-NODECISION', status: 'RUNNING' }))
      .mockResolvedValueOnce(jsonResponse({
        agentEffectivePermission: { allowedTools: [], allowedData: [] },
        withheldTools: [],
      }))
      .mockResolvedValueOnce(jsonResponse({
        agentRunId: 'RUN-NODECISION',
        status: 'COMPLETED',
        attempts: [{
          requestId: '00000000-0000-4000-8000-00000000f002',
          requestedTool: 'CREDIT_SCORE_READ',
          systemOutcome: 'COMPLETED',
        }],
      }))
    configureFinboundApi({ mode: 'real', credential: 'operator', fetchImpl })

    await expect(finboundApi.executeAgentTask({ workId: 'NEW_LOAN' })).rejects.toMatchObject({
      code: 'CORE_API_INVALID_RESPONSE',
    })
  })

  it('keeps unavailable reachability facts unknown', async () => {
    const fetchImpl = vi.fn()
      .mockResolvedValueOnce(jsonResponse({ agentRunId: 'RUN-UNKNOWN', status: 'RUNNING' }))
      .mockResolvedValueOnce(jsonResponse({
        agentEffectivePermission: { allowedTools: [], allowedData: [] },
        withheldTools: [],
      }))
      .mockResolvedValueOnce(jsonResponse({
        agentRunId: 'RUN-UNKNOWN',
        status: 'COMPLETED',
        attempts: [{
          requestId: 'REQ-UNKNOWN',
          decision: 'ALLOW',
          systemOutcome: 'COMPLETED',
        }],
      }))
    configureFinboundApi({ mode: 'real', credential: 'operator', fetchImpl })

    const result = await finboundApi.executeAgentTask({ workId: 'NEW_LOAN' })

    expect(result.attempts[0].downstreamReached).toBeNull()
    expect(result.attempts[0].responseReleased).toBeNull()
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
