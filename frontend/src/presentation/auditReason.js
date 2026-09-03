const reasonDescriptions = {
  CASE_SCOPE_VIOLATION: '현재 심사 건과 관련 없는 고객 자료가 포함되어 조회 전에 차단했습니다.',
  MANDATE_SCOPE_VIOLATION: '현재 고객 동의 범위에 포함되지 않은 자료라 조회 전에 차단했습니다.',
  PROMPT_INJECTION: 'AI 입력에서 업무 지시를 바꾸려는 위험 신호를 확인해 실행 전에 차단했습니다.',
  BEHAVIOR_ANOMALY: '평소 업무 흐름과 다른 AI 행동을 확인해 실행 전에 차단했습니다.',
  DOWNSTREAM_TIMEOUT: '금융시스템 응답이 지연되어 결과를 직원에게 제공하지 못했습니다.',
  DOWNSTREAM_ERROR: '금융시스템 처리 중 오류가 발생해 결과를 직원에게 제공하지 못했습니다.',
}

export function describeAuditReason(event) {
  const reasonCode = event?.reasonCodes?.[0]
  if (reasonCode && reasonDescriptions[reasonCode]) return reasonDescriptions[reasonCode]
  if (reasonCode) return '처리 사유 설명이 제공되지 않았습니다.'
  if (event?.auditStatus === 'ERROR' || event?.decision === 'BLOCK') return '처리 사유를 확인할 수 없습니다.'
  if (event?.decision === 'ALLOW' && event?.auditStatus === 'COMPLETED') {
    return '요청한 업무 범위 안에서 정상 처리했습니다.'
  }
  return '처리 상태와 사유를 확인할 수 없습니다.'
}
