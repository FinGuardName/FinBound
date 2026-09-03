<script setup>
import { computed, onMounted, ref, watch } from 'vue'

import StatusBadge from '../components/StatusBadge.vue'
import { finboundApi } from '../services/finboundApi'

const workCatalog = ref([])
const selectedWorkId = ref('')
const execution = ref(null)
const executionError = ref('')
const loading = ref(false)

onMounted(async () => {
  workCatalog.value = await finboundApi.getBankWorkCatalog()
  selectedWorkId.value = workCatalog.value[0]?.id ?? ''
})

const workContext = computed(() => workCatalog.value.find((work) => work.id === selectedWorkId.value))
const protectionContext = computed(() => {
  const mockPassport = workContext.value?.passport ?? {}
  const agentRun = execution.value?.agentRun
  const permission = execution.value?.permission
  const effectivePermission = permission?.agentEffectivePermission

  if (!agentRun || !effectivePermission) {
    if (finboundApi.isRealMode()) {
      const unavailableLabel = executionError.value ? '확인 불가' : '실행 전'
      return {
        agentRunId: agentRun?.agentRunId ?? unavailableLabel,
        passportId: agentRun?.passportId ?? unavailableLabel,
        expiresAtLabel: agentRun?.expiresAt ?? unavailableLabel,
        allowedTools: effectivePermission?.allowedTools ?? null,
        allowedData: effectivePermission?.allowedData ?? null,
        withheldTools: permission?.withheldTools ?? null,
        source: 'core',
      }
    }
    return {
      agentRunId: mockPassport.agentRunId,
      passportId: mockPassport.passportId,
      expiresAtLabel: mockPassport.expiresAtLabel,
      allowedTools: mockPassport.allowedTools ?? [],
      allowedData: mockPassport.allowedData ?? [],
      withheldTools: [],
      source: 'preview',
    }
  }

  return {
    agentRunId: agentRun.agentRunId ?? '미제공',
    passportId: agentRun.passportId ?? '미제공',
    expiresAtLabel: agentRun.expiresAt ?? 'Core 응답 미제공',
    allowedTools: effectivePermission.allowedTools ?? [],
    allowedData: effectivePermission.allowedData ?? [],
    withheldTools: permission.withheldTools ?? [],
    source: 'core',
  }
})
const permissionListLabel = (values) => (
  Array.isArray(values) ? (values.join(' · ') || '없음') : '확인 불가'
)
const allowedAttempts = computed(() => execution.value?.attempts.filter((attempt) => attempt.decision === 'ALLOW' && attempt.systemOutcome !== 'ERROR') ?? [])
const blockedAttempts = computed(() => execution.value?.attempts.filter((attempt) => attempt.decision === 'BLOCK' && attempt.systemOutcome !== 'ERROR') ?? [])
const errorAttempts = computed(() => execution.value?.attempts.filter((attempt) => attempt.systemOutcome === 'ERROR') ?? [])
const attemptDisplayOutcome = (attempt) => attempt.systemOutcome === 'ERROR' ? 'ERROR' : (attempt.decision ?? 'UNKNOWN')
const attemptStatusLabel = (attempt) => {
  if (attempt.systemOutcome === 'ERROR') return '처리 오류'
  if (attempt.decision === 'ALLOW') return '확인 완료'
  if (attempt.decision === 'BLOCK') return '조회 차단'
  return '결과 미제공'
}
const attemptScopeLabel = (attempt) => {
  if (attempt.systemOutcome === 'ERROR') return '시스템 처리 오류'
  if (attempt.decision === 'ALLOW') return '현재 업무에 필요'
  if (attempt.decision === 'BLOCK') return '현재 업무 범위 밖'
  return '업무 범위 확인 불가'
}
const booleanStatusLabel = (value, trueLabel, falseLabel) => {
  if (value === true) return trueLabel
  if (value === false) return falseLabel
  return '확인 불가'
}
const executionStateLabel = computed(() => {
  if (execution.value?.status === 'RUNNING') return '업무 실행 중'
  if (execution.value?.status === 'ERROR' || errorAttempts.value.length) return '업무 오류'
  return blockedAttempts.value.length ? `업무 완료 · 보호 ${blockedAttempts.value.length}건` : '업무 완료'
})
const isReviewReady = computed(() => Boolean(
  execution.value
  && !executionError.value
  && execution.value.status !== 'ERROR'
  && !loading.value,
))

watch(selectedWorkId, () => {
  execution.value = null
  executionError.value = ''
})

async function runAgentTask() {
  loading.value = true
  execution.value = null
  executionError.value = ''
  try {
    execution.value = await finboundApi.executeAgentTask({ workId: selectedWorkId.value })
  } catch (error) {
    if (error?.executionContext) {
      execution.value = {
        status: 'ERROR',
        attempts: [],
        ...error.executionContext,
      }
    }
    executionError.value = '업무 처리 결과를 확인하지 못했습니다. 금융시스템 조회 여부는 업무 기록에서 확인해 주세요.'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section v-if="workContext" class="agent-workspace" aria-labelledby="run-heading">
    <div class="work-catalog" aria-labelledby="catalog-heading">
      <div class="catalog-heading">
        <div><p class="section-kicker">여신 업무 선택</p><h2 id="catalog-heading">확인할 여신 업무를 선택해 주세요</h2><p>업무마다 고객·자료 범위와 보호 설정이 다르게 적용됩니다.</p></div>
      </div>
      <div class="work-card-grid">
        <button v-for="(work, index) in workCatalog" :key="work.id" :data-work="work.id" :class="['work-card', { active: selectedWorkId === work.id }]" :aria-pressed="selectedWorkId === work.id" type="button" @click="selectedWorkId = work.id">
          <span class="work-icon" aria-hidden="true">
            <svg v-if="index === 0" viewBox="0 0 24 24"><path d="M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8Zm-7 8a7 7 0 0 1 14 0M18 6v5M15.5 8.5h5" /></svg>
            <svg v-else-if="index === 1" viewBox="0 0 24 24"><path d="M7 4h10l2 5-2 3 2 3-2 5H7l-2-5 2-3-2-3zM8 9h8M8 15h8" /></svg>
            <svg v-else viewBox="0 0 24 24"><path d="M8 3h8v4H8zM6 5H4v16h16V5h-2M8 12h8M8 16h5" /></svg>
          </span>
          <span class="work-card-copy"><span class="work-badge">{{ work.badge }}</span><strong>{{ work.shortLabel }}</strong><small>{{ work.summary }}</small></span>
          <span v-if="selectedWorkId === work.id" class="work-selection-indicator" aria-label="선택됨">✓</span>
          <span v-else class="work-card-action" aria-hidden="true">›</span>
        </button>
      </div>
    </div>

    <div class="bank-work-grid">
      <article class="panel bank-workbench">
        <div class="panel-heading case-heading">
          <div><p class="section-kicker">현재 업무</p><h2 id="run-heading">{{ workContext.case.productName }}</h2><p class="case-reference">{{ workContext.case.caseId }}</p></div>
          <span class="case-state"><i aria-hidden="true"></i>{{ workContext.case.statusLabel }}</span>
        </div>

        <div class="work-detail-grid">
          <section class="employee-detail" aria-label="담당 직원 정보">
            <div class="detail-label"><span class="detail-icon" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8Zm-7 8a7 7 0 0 1 14 0" /></svg></span>담당 직원 정보</div>
            <div class="employee-profile">
              <span class="employee-avatar" aria-hidden="true">{{ workContext.employee.name.slice(0, 1) }}</span>
              <div><strong>{{ workContext.employee.name }}</strong><small>{{ workContext.employee.branch }}</small><em>{{ workContext.employee.id }}</em></div>
            </div>
          </section>
          <section class="application-detail" aria-label="대출 신청 정보">
            <div class="detail-label"><span class="detail-icon" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="M7 3h10v4h3v14H4V7h3zM8 12h8M8 16h5" /></svg></span>대출 신청 정보</div>
            <dl class="case-facts">
              <div><dt>고객</dt><dd>{{ workContext.case.consumerLabel }} <small>{{ workContext.case.consumerId }}</small></dd></div>
              <div><dt>신청 금액</dt><dd>{{ workContext.case.applicationAmountLabel }}</dd></div>
              <div><dt>신청 목적</dt><dd>{{ workContext.case.purpose }}</dd></div>
              <div><dt>접수 일시</dt><dd>{{ workContext.case.receivedAtLabel }}</dd></div>
            </dl>
          </section>
        </div>

        <div class="review-status" aria-label="대출 심사 진행 단계">
          <span class="complete"><i>✓</i><strong>신청 접수</strong><small>완료</small></span><b class="complete-line"></b>
          <span :class="isReviewReady ? 'complete' : 'current'"><i>{{ isReviewReady ? '✓' : '2' }}</i><strong>자료 확인</strong><small>{{ isReviewReady ? '완료' : '진행 중' }}</small></span><b :class="{ 'complete-line': isReviewReady }"></b>
          <span :class="{ current: isReviewReady }"><i>3</i><strong>심사 의견</strong><small>{{ isReviewReady ? '진행 중' : '예정' }}</small></span>
        </div>

        <form class="agent-task-form" @submit.prevent="runAgentTask">
          <div class="assistant-intro">
            <span class="assistant-avatar" aria-hidden="true"><svg class="soft-shield-icon" viewBox="0 0 24 24"><path class="shield-fill" d="M12 2.7c2.35 1.45 4.75 2.35 7.2 2.9v5.15c0 4.75-2.8 8.4-7.2 10.55-4.4-2.15-7.2-5.8-7.2-10.55V5.6c2.45-.55 4.85-1.45 7.2-2.9Z" /><path class="shield-symbol" d="M12 8v8M8 12h8" /></svg></span>
            <div><h3>AI 업무 도우미</h3><p>반복적인 자료 확인을 대신하고, 결과를 현재 대출 신청 건에 정리합니다.</p></div>
            <span class="protected-label">업무 보호 적용</span>
          </div>
          <div class="employee-request">
            <span class="employee-request-icon" aria-hidden="true">✓</span>
            <div><small>직원이 요청한 업무</small><strong>{{ workContext.employeeRequest.title }}</strong><p>{{ workContext.employeeRequest.description }}</p></div>
            <span class="request-scope-label">현재 신청 건</span>
          </div>
          <button class="primary-button run-agent-button" type="submit" :disabled="loading">
            <span aria-hidden="true">✦</span>{{ loading ? '업무 처리 중…' : 'AI로 이 업무 진행' }}
          </button>
          <p class="employee-guide">직원은 업무만 요청합니다. AI가 확인할 고객과 자료 범위는 현재 신청 건을 기준으로 시스템이 자동 결정합니다.</p>
        </form>
      </article>

      <aside class="panel guardrail-panel" aria-label="AI 업무 보호 설정">
        <div class="guardrail-heading">
          <div class="guard-shield" aria-hidden="true"><svg class="soft-shield-icon" viewBox="0 0 24 24"><path class="shield-fill" d="M12 2.7c2.35 1.45 4.75 2.35 7.2 2.9v5.15c0 4.75-2.8 8.4-7.2 10.55-4.4-2.15-7.2-5.8-7.2-10.55V5.6c2.45-.55 4.85-1.45 7.2-2.9Z" /><path class="shield-symbol" d="m8.5 12 2.25 2.25 4.8-5" /></svg></div>
          <div><p class="section-kicker">Permission Boundary</p><h2>AI 업무 보호 설정</h2></div>
          <span class="protection-on"><i></i>적용 중</span>
        </div>
        <p class="guardrail-copy">AI는 직원의 모든 권한을 사용하지 않습니다. 지금 처리하는 대출 신청 건에 필요한 범위 안에서만 자료를 확인합니다.</p>

        <div class="permission-boundary">
          <div class="authority-scope">
            <span>직원이 조회할 수 있는 범위</span>
            <strong>{{ workContext.employee.authorityLabel }}</strong>
            <small>{{ workContext.employee.authorityScope }}</small>
          </div>
          <div class="boundary-narrowing" aria-hidden="true"><span>→</span><small>최소 권한</small></div>
          <div class="authority-scope effective">
            <span>이번 업무에서 AI가 확인하는 범위</span>
            <strong>{{ workContext.case.consumerLabel }} 1명</strong>
            <small>{{ workContext.case.consumerId }} 전용</small>
          </div>
        </div>

        <div class="guardrail-lower">
          <div>
            <p class="guardrail-subtitle">권한 적용 원칙</p>
            <ul class="protection-rules">
              <li><i aria-hidden="true"><svg viewBox="0 0 24 24"><path d="M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8Zm-7 8a7 7 0 0 1 14 0" /></svg></i><span><strong>현재 대출 신청 건의 자료만</strong>다른 고객이나 다른 업무의 자료는 확인하지 않습니다.</span></li>
              <li><i aria-hidden="true"><svg class="soft-shield-icon" viewBox="0 0 24 24"><path class="shield-fill" d="M12 2.7c2.35 1.45 4.75 2.35 7.2 2.9v5.15c0 4.75-2.8 8.4-7.2 10.55-4.4-2.15-7.2-5.8-7.2-10.55V5.6c2.45-.55 4.85-1.45 7.2-2.9Z" /><path class="shield-symbol" d="m8.5 12 2.25 2.25 4.8-5" /></svg></i><span><strong>고객 동의 범위 안에서만</strong>심사에 필요한 신용·소득·부채 자료로 제한합니다.</span></li>
              <li><i aria-hidden="true"><svg viewBox="0 0 24 24"><path d="M7 10V7a5 5 0 0 1 10 0v3M5 10h14v11H5zM12 14v3" /></svg></i><span><strong>금융시스템 조회 전에 확인</strong>범위를 벗어난 요청은 자료 조회 전에 중단합니다.</span></li>
            </ul>
          </div>

          <div class="security-visual" role="img" aria-label="직원과 고객, 금융시스템 사이에서 AI 권한이 현재 업무 범위로 제한되는 구조">
            <span class="security-orbit orbit-one"></span><span class="security-orbit orbit-two"></span>
            <span class="security-node employee-node"><svg viewBox="0 0 24 24"><path d="M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8Zm-6 8a6 6 0 0 1 12 0" /></svg><small>직원</small></span>
            <span class="security-node finance-node"><svg viewBox="0 0 24 24"><path d="M5 7h14M7 7V4h10v3M7 10v8M12 10v8M17 10v8M5 20h14" /></svg><small>금융시스템</small></span>
            <span class="security-node case-node"><svg viewBox="0 0 24 24"><path d="M7 3h10v4h3v14H4V7h3zM8 12h8M8 16h5" /></svg><small>신청 건</small></span>
            <span class="security-core"><svg class="soft-shield-icon" viewBox="0 0 24 24"><path class="shield-fill" d="M12 2.7c2.35 1.45 4.75 2.35 7.2 2.9v5.15c0 4.75-2.8 8.4-7.2 10.55-4.4-2.15-7.2-5.8-7.2-10.55V5.6c2.45-.55 4.85-1.45 7.2-2.9Z" /><path class="shield-symbol" d="m8.5 12 2.25 2.25 4.8-5" /></svg><strong>AI</strong></span>
          </div>
        </div>

        <section class="security-details security-details-fixed" aria-labelledby="security-details-heading">
          <div class="security-details-heading">
            <span class="security-details-icon" aria-hidden="true"><svg class="soft-shield-icon" viewBox="0 0 24 24"><path class="shield-fill" d="M12 2.7c2.35 1.45 4.75 2.35 7.2 2.9v5.15c0 4.75-2.8 8.4-7.2 10.55-4.4-2.15-7.2-5.8-7.2-10.55V5.6c2.45-.55 4.85-1.45 7.2-2.9Z" /><path class="shield-symbol" d="m8.5 12 2.25 2.25 4.8-5" /></svg></span>
            <div><strong id="security-details-heading">시스템 정보</strong><small>AI는 담당 직원보다 더 많은 정보에 접근할 수 없습니다.</small></div>
          </div>
          <dl>
            <div><dt>업무 번호</dt><dd>{{ workContext.case.caseId }}</dd></div>
            <div><dt>AI 실행 번호</dt><dd>{{ protectionContext.agentRunId }}</dd></div>
            <div><dt>권한 확인서</dt><dd>{{ protectionContext.passportId }}</dd></div>
            <div><dt>유효 시간</dt><dd>{{ protectionContext.expiresAtLabel }}</dd></div>
          </dl>
          <div class="security-permissions">
            <p>허용 업무: {{ permissionListLabel(protectionContext.allowedTools) }}</p>
            <p>허용 자료: {{ permissionListLabel(protectionContext.allowedData) }}</p>
            <p v-if="protectionContext.source === 'core'">권한 제외 업무: {{ permissionListLabel(protectionContext.withheldTools) }}</p>
          </div>
          <small>Agent Effective Permission ⊆ Employee Authority</small>
        </section>
      </aside>
    </div>

    <article class="panel execution-panel" aria-live="polite">
      <div v-if="executionError" class="execution-error" role="alert">
        <span aria-hidden="true">!</span><div><h2>업무 처리 상태를 확인할 수 없습니다</h2><p>{{ executionError }}</p></div>
      </div>
      <div v-else-if="!execution" class="execution-empty">
        <span class="empty-document" aria-hidden="true">✓</span>
        <div><p class="section-kicker">심사자료 확인 대기</p><h2>AI 업무 도우미를 시작해 주세요</h2><p>확인 과정과 결과는 현재 대출 신청 건에만 표시됩니다.</p></div>
      </div>

      <template v-else>
        <div class="decision-banner protected">
          <div class="decision-icon" aria-hidden="true">✓</div>
          <div><p class="section-kicker">AI 업무 처리 결과</p><h2>{{ execution.title }}</h2><p>{{ execution.message }}</p></div>
          <span class="plain-decision">{{ executionStateLabel }}</span>
        </div>

        <div class="execution-content">
          <div class="work-progress">
            <div class="attempt-heading"><div><p class="section-kicker">AI가 시도한 작업</p><h3>자료별 접근 결과</h3></div><span>{{ execution.status === 'RUNNING' ? '실행 결과 대기 중' : `${allowedAttempts.length}건 확인 · ${blockedAttempts.length}건 차단 · ${errorAttempts.length}건 오류` }}</span></div>
            <p v-if="execution.status === 'RUNNING'" class="no-results">AgentRun이 생성되었습니다. Tool Call 결과는 감사 현황에서 확인할 수 있습니다.</p>
            <ol class="attempt-list" aria-label="AI 자료 접근 결과">
              <li v-for="attempt in execution.attempts" :key="attempt.requestId" :class="attemptDisplayOutcome(attempt).toLowerCase()">
                <span class="timeline-marker">{{ attempt.decision === 'ALLOW' && attempt.systemOutcome !== 'ERROR' ? '✓' : '!' }}</span>
                <div class="attempt-copy">
                  <small>{{ attemptScopeLabel(attempt) }}</small>
                  <strong>{{ attempt.label }}</strong>
                  <p>{{ attempt.description }}</p>
                  <details class="attempt-details">
                    <summary>보안 처리 내역</summary>
                    <dl>
                      <div><dt>요청 번호</dt><dd>{{ attempt.requestId }}</dd></div>
                      <div><dt>요청 고객</dt><dd>{{ attempt.targetConsumerId }}</dd></div>
                      <div><dt>업무 범위</dt><dd><StatusBadge :value="attempt.scopeStatus.customerScope" /></dd></div>
                      <div><dt>처리 사유</dt><dd class="reason-code">{{ attempt.reasonCodes[0] || (attempt.decision === 'ALLOW' && attempt.systemOutcome === 'COMPLETED' ? '차단 사유 없음' : '처리 사유 미제공') }}</dd></div>
                      <div><dt>금융시스템 요청</dt><dd>{{ booleanStatusLabel(attempt.downstreamReached, '전달됨', '전달 안 됨') }}</dd></div>
                      <div><dt>결과 제공</dt><dd>{{ booleanStatusLabel(attempt.responseReleased, '제공함', '제공 안 함') }}</dd></div>
                      <div><dt>도구</dt><dd>{{ attempt.tool }}</dd></div>
                      <div><dt>자료</dt><dd>{{ attempt.requestedData.join(' · ') }}</dd></div>
                    </dl>
                  </details>
                </div>
                <span class="attempt-decision">{{ attemptStatusLabel(attempt) }}</span>
              </li>
            </ol>
          </div>

          <aside class="result-summary">
            <p class="section-kicker">직원이 확인할 내용</p>
            <h3>{{ execution.resultHeading }}</h3>
            <ul><li v-for="item in execution.resultItems" :key="item">{{ item }}</li></ul>
            <div v-if="blockedAttempts.length" class="protection-summary"><strong>FinBound 보호 작동</strong><p>차단된 추가 조회는 금융시스템에 전달되지 않았습니다. 현재 고객의 정상 심사자료만 결과에 포함했습니다.</p></div>
            <div class="next-action safe"><strong>다음 업무</strong>{{ execution.nextAction }}</div>
          </aside>
        </div>
      </template>
    </article>
  </section>
</template>
