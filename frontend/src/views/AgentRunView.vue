<script setup>
import { onMounted, ref } from 'vue'

import StatusBadge from '../components/StatusBadge.vue'
import { finguardApi } from '../services/finguardApi'

const workContext = ref(null)
const selectedScenario = ref('IN_SCOPE')
const execution = ref(null)
const executionError = ref('')
const loading = ref(false)

onMounted(async () => {
  workContext.value = await finguardApi.getBankWorkContext()
})

async function runAgentTask() {
  loading.value = true
  execution.value = null
  executionError.value = ''
  try {
    execution.value = await finguardApi.executeAgentTask({ scenario: selectedScenario.value })
  } catch {
    executionError.value = '업무를 처리하지 못했습니다. 고객 자료는 조회하지 않았습니다. 잠시 후 다시 시도해 주세요.'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section v-if="workContext" class="agent-workspace" aria-labelledby="run-heading">
    <div class="work-session-bar">
      <div>
        <p class="work-breadcrumb">대출 업무 <span>›</span> 신규 심사 <span>›</span> 신청 상세</p>
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
          <fieldset>
            <legend>어떤 자료를 확인할까요?</legend>
            <label :class="['task-option', { selected: selectedScenario === 'IN_SCOPE' }]">
              <input v-model="selectedScenario" type="radio" value="IN_SCOPE" />
              <span class="task-option-icon safe" aria-hidden="true">✓</span>
              <span><strong>현재 신청 고객의 심사자료 확인</strong><small>신용정보·소득·부채 자료를 한 번에 확인합니다.</small></span>
              <em>기본</em>
            </label>
            <label :class="['task-option', { selected: selectedScenario === 'OUT_OF_SCOPE' }]">
              <input v-model="selectedScenario" type="radio" value="OUT_OF_SCOPE" />
              <span class="task-option-icon compare" aria-hidden="true">＋</span>
              <span><strong>유사 고객 자료까지 함께 비교</strong><small>현재 신청 건 외 참고 고객 자료를 포함해 요청합니다.</small></span>
              <em>추가 검토</em>
            </label>
          </fieldset>
          <button class="primary-button run-agent-button" type="submit" :disabled="loading">
            <span aria-hidden="true">✦</span>{{ loading ? '심사자료 확인 중…' : 'AI로 심사자료 확인' }}
          </button>
          <p class="employee-guide">직원은 평소처럼 필요한 업무만 선택하면 됩니다. AI의 자료 접근 범위는 시스템이 자동으로 확인합니다.</p>
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
            <div><dt>AI 실행 번호</dt><dd>RUN-001</dd></div>
            <div><dt>권한 확인서</dt><dd>{{ workContext.passport.passportId }}</dd></div>
            <div><dt>유효 시간</dt><dd>{{ workContext.passport.expiresAtLabel }}</dd></div>
          </dl>
          <p>허용 업무: {{ workContext.passport.allowedTools.join(' · ') }}</p>
          <small>Agent Effective Permission ⊆ Employee Authority</small>
        </details>
      </aside>
    </div>

    <article class="panel execution-panel" aria-live="polite">
      <div v-if="executionError" class="execution-error" role="alert">
        <span aria-hidden="true">!</span><div><h2>자료를 조회하지 않았습니다</h2><p>{{ executionError }}</p></div>
      </div>
      <div v-else-if="!execution" class="execution-empty">
        <span class="empty-document" aria-hidden="true">✓</span>
        <div><p class="section-kicker">심사자료 확인 대기</p><h2>AI 업무 도우미를 시작해 주세요</h2><p>확인 과정과 결과는 현재 대출 신청 건에만 표시됩니다.</p></div>
      </div>

      <template v-else>
        <div :class="['decision-banner', execution.decision.toLowerCase()]">
          <div class="decision-icon" aria-hidden="true">{{ execution.decision === 'ALLOW' ? '✓' : '!' }}</div>
          <div><p class="section-kicker">AI 업무 처리 결과</p><h2>{{ execution.title }}</h2><p>{{ execution.message }}</p></div>
          <span class="plain-decision">{{ execution.decision === 'ALLOW' ? '확인 완료' : '안전 중단' }}</span>
        </div>

        <div class="execution-content">
          <div class="work-progress">
            <h3>처리 과정</h3>
            <ol aria-label="AI 업무 처리 단계">
              <li v-for="step in execution.steps" :key="step.id" :class="step.state.toLowerCase()">
                <span class="timeline-marker">{{ step.state === 'ALLOW' ? '✓' : '!' }}</span>
                <div><small>{{ step.label }}</small><strong>{{ step.title }}</strong><p>{{ step.description }}</p></div>
              </li>
            </ol>
          </div>

          <aside class="result-summary">
            <p class="section-kicker">직원이 확인할 내용</p>
            <template v-if="execution.decision === 'ALLOW'">
              <h3>심사자료가 준비되었습니다</h3>
              <ul><li>신용정보 확인 완료</li><li>소득·부채 자료 확인 완료</li><li>현재 신청 건에 결과 연결 완료</li></ul>
              <div class="next-action safe"><strong>다음 업무</strong>확인된 자료를 바탕으로 심사 의견을 작성해 주세요.</div>
            </template>
            <template v-else>
              <h3>요청 범위를 다시 확인해 주세요</h3>
              <ul><li>현재 신청 고객 외 자료가 포함됨</li><li>다른 고객 자료는 조회하지 않음</li><li>현재 신청 건은 그대로 유지됨</li></ul>
              <div class="next-action blocked"><strong>다음 업무</strong>‘현재 신청 고객의 심사자료 확인’을 선택해 다시 실행해 주세요.</div>
            </template>

            <details class="security-details result-details">
              <summary>보안 처리 내역 보기</summary>
              <dl>
                <div><dt>요청 번호</dt><dd>{{ execution.requestId }}</dd></div>
                <div><dt>요청 고객</dt><dd>{{ execution.targetConsumerId }}</dd></div>
                <div><dt>업무 범위</dt><dd><StatusBadge :value="execution.scopeStatus.customerScope" /></dd></div>
                <div><dt>처리 코드</dt><dd class="reason-code">{{ execution.reasonCodes[0] || 'POLICY_REQUIREMENTS_MET' }}</dd></div>
                <div><dt>금융시스템 조회</dt><dd>{{ execution.downstreamReached ? '완료 · 1회' : '조회 안 함 · 0회' }}</dd></div>
              </dl>
              <p>Tool: {{ execution.tool }}</p>
            </details>
          </aside>
        </div>
      </template>
    </article>
  </section>
</template>
