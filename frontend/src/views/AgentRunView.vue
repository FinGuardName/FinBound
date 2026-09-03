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
    <div class="panel work-catalog" aria-labelledby="catalog-heading">
      <div class="catalog-heading">
        <div><p class="section-kicker">AI 업무 시뮬레이션</p><h2 id="catalog-heading">확인할 여신 업무를 선택해 주세요</h2><p>업무마다 고객·자료 범위와 보호 설정이 다르게 적용됩니다.</p></div>
        <span>{{ workCatalog.length }}개 업무</span>
      </div>
      <div class="work-card-grid">
        <button v-for="work in workCatalog" :key="work.id" :data-work="work.id" :class="['work-card', { active: selectedWorkId === work.id }]" type="button" @click="selectedWorkId = work.id">
          <span class="work-badge">{{ work.badge }}</span>
          <strong>{{ work.shortLabel }}</strong>
          <small>{{ work.summary }}</small>
          <em>{{ selectedWorkId === work.id ? '선택됨' : '시뮬레이션 보기' }} <i aria-hidden="true">›</i></em>
        </button>
      </div>
    </div>

    <div class="work-session-bar">
      <div>
        <p class="work-breadcrumb">여신 업무 <span>›</span> {{ workContext.shortLabel }} <span>›</span> 업무 상세</p>
        <h2 id="run-heading">{{ workContext.case.productName }}</h2>
      </div>
      <div class="employee-session">
        <span class="employee-avatar" aria-hidden="true">{{ workContext.employee.name.slice(0, 1) }}</span>
        <div><strong>{{ workContext.employee.name }}</strong><small>{{ workContext.employee.branch }} · {{ workContext.employee.id }}</small></div>
      </div>
    </div>

    <div class="bank-work-grid">
      <article class="panel bank-workbench">
        <div class="panel-heading case-heading">
          <div><p class="section-kicker">대출 신청 정보</p><h2>{{ workContext.case.caseId }}</h2></div>
          <span class="case-state"><i aria-hidden="true"></i>{{ workContext.case.statusLabel }}</span>
        </div>

        <dl class="case-facts">
          <div><dt>신청 고객</dt><dd>{{ workContext.case.consumerLabel }} <small>{{ workContext.case.consumerId }}</small></dd></div>
          <div><dt>신청 금액</dt><dd>{{ workContext.case.applicationAmountLabel }}</dd></div>
          <div><dt>신청 목적</dt><dd>{{ workContext.case.purpose }}</dd></div>
          <div><dt>접수 일시</dt><dd>{{ workContext.case.receivedAtLabel }}</dd></div>
        </dl>

        <div class="review-status" aria-label="대출 심사 진행 단계">
          <span class="complete"><i>✓</i>신청 접수</span><b></b>
          <span class="current"><i>2</i>자료 확인</span><b></b>
          <span><i>3</i>심사 의견</span>
        </div>

        <form class="agent-task-form" @submit.prevent="runAgentTask">
          <div class="assistant-intro">
            <span class="assistant-avatar" aria-hidden="true">AI</span>
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
          <div class="guard-shield" aria-hidden="true">✓</div>
          <div><p class="section-kicker">안전한 AI 업무 지원</p><h2>AI 업무 보호 설정</h2></div>
          <span class="protection-on"><i></i>적용 중</span>
        </div>
        <p class="guardrail-copy">AI는 직원의 모든 권한을 사용하지 않습니다. 지금 처리하는 대출 신청 건에 필요한 범위 안에서만 자료를 확인합니다.</p>

        <div class="permission-boundary">
          <div class="authority-scope">
            <span>직원이 조회할 수 있는 범위</span>
            <strong>{{ workContext.employee.authorityLabel }}</strong>
            <small>{{ workContext.employee.authorityScope }}</small>
          </div>
          <div class="boundary-narrowing" aria-hidden="true"><span>›</span><small>현재 업무로 제한</small></div>
          <div class="authority-scope effective">
            <span>이번 업무에서 AI가 확인하는 범위</span>
            <strong>{{ workContext.case.consumerLabel }} 1명</strong>
            <small>{{ workContext.case.consumerId }} 전용</small>
          </div>
        </div>

        <ul class="protection-rules">
          <li><i aria-hidden="true">✓</i><span><strong>현재 대출 신청 건의 자료만</strong>다른 고객이나 다른 업무의 자료는 확인하지 않습니다.</span></li>
          <li><i aria-hidden="true">✓</i><span><strong>고객 동의 범위 안에서만</strong>심사에 필요한 신용·소득·부채 자료로 제한합니다.</span></li>
          <li><i aria-hidden="true">✓</i><span><strong>금융시스템 조회 전에 확인</strong>범위를 벗어난 요청은 자료 조회 전에 중단합니다.</span></li>
        </ul>

        <p class="plain-invariant">AI는 담당 직원보다 더 많은 정보에 접근할 수 없습니다.</p>

        <details class="security-details">
          <summary>시스템 정보 보기</summary>
          <dl>
            <div><dt>업무 번호</dt><dd>{{ workContext.case.caseId }}</dd></div>
            <div><dt>AI 실행 번호</dt><dd>{{ protectionContext.agentRunId }}</dd></div>
            <div><dt>권한 확인서</dt><dd>{{ protectionContext.passportId }}</dd></div>
            <div><dt>유효 시간</dt><dd>{{ protectionContext.expiresAtLabel }}</dd></div>
          </dl>
          <p>허용 업무: {{ permissionListLabel(protectionContext.allowedTools) }}</p>
          <p>허용 자료: {{ permissionListLabel(protectionContext.allowedData) }}</p>
          <p v-if="protectionContext.source === 'core'">권한 제외 업무: {{ permissionListLabel(protectionContext.withheldTools) }}</p>
          <small>Agent Effective Permission ⊆ Employee Authority</small>
        </details>
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
