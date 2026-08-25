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
    executionError.value = 'Agent 요청을 처리하지 못했습니다. 안전을 위해 금융 API를 호출하지 않았습니다.'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section v-if="workContext" class="agent-workspace" aria-labelledby="run-heading">
    <div class="work-session-bar">
      <div>
        <p class="section-kicker">은행 업무 시스템 · 대출 심사</p>
        <h2 id="run-heading">{{ workContext.case.productName }}</h2>
      </div>
      <div class="employee-session">
        <span class="employee-avatar" aria-hidden="true">{{ workContext.employee.name.slice(0, 1) }}</span>
        <div><strong>{{ workContext.employee.name }}</strong><small>{{ workContext.employee.branch }} · {{ workContext.employee.id }}</small></div>
        <StatusBadge value="ACTIVE" />
      </div>
    </div>

    <div class="bank-work-grid">
      <article class="panel bank-workbench">
        <div class="panel-heading case-heading">
          <div>
            <p class="section-kicker">Current financial case</p>
            <h2>{{ workContext.case.caseId }}</h2>
          </div>
          <span class="case-state"><i aria-hidden="true"></i>{{ workContext.case.statusLabel }}</span>
        </div>

        <dl class="case-facts">
          <div><dt>심사 고객</dt><dd>{{ workContext.case.consumerLabel }} <small>{{ workContext.case.consumerId }}</small></dd></div>
          <div><dt>업무 목적</dt><dd>{{ workContext.case.purpose }}</dd></div>
          <div><dt>담당 업무</dt><dd>{{ workContext.case.taskLabel }}</dd></div>
          <div><dt>업무 기한</dt><dd>{{ workContext.case.expiresAtLabel }}</dd></div>
        </dl>

        <form class="agent-task-form" @submit.prevent="runAgentTask">
          <fieldset>
            <legend>AI Agent에게 요청할 심사 보조 업무</legend>
            <label :class="['task-option', { selected: selectedScenario === 'IN_SCOPE' }]">
              <input v-model="selectedScenario" type="radio" value="IN_SCOPE" />
              <span class="task-option-icon safe" aria-hidden="true">✓</span>
              <span><strong>현재 고객 신용정보 조회</strong><small>CUST-1001 · CREDIT_SCORE_READ</small></span>
              <em>정상 업무</em>
            </label>
            <label :class="['task-option', { selected: selectedScenario === 'OUT_OF_SCOPE' }]">
              <input v-model="selectedScenario" type="radio" value="OUT_OF_SCOPE" />
              <span class="task-option-icon blocked" aria-hidden="true">!</span>
              <span><strong>다른 고객 신용정보 조회 시도</strong><small>CUST-9999 · CREDIT_SCORE_READ</small></span>
              <em>차단 데모</em>
            </label>
          </fieldset>

          <div class="agent-handoff-note">
            <span class="agent-symbol" aria-hidden="true">AI</span>
            <p><strong>은행원은 평소처럼 업무를 요청합니다.</strong>Agent가 금융 Tool을 호출하는 순간 FinGuard가 현재 Case와 권한을 자동 확인합니다.</p>
          </div>
          <button class="primary-button run-agent-button" type="submit" :disabled="loading">
            <span aria-hidden="true">▶</span>{{ loading ? 'FinGuard 사전 검증 중…' : 'AI Agent에 심사 보조 요청' }}
          </button>
        </form>
      </article>

      <aside class="panel guardrail-panel" aria-label="FinGuard 권한 경계">
        <div class="guardrail-heading">
          <div class="guard-shield" aria-hidden="true">F</div>
          <div><p class="section-kicker">Runtime guardrail</p><h2>FinGuard 보호 상태</h2></div>
          <span class="live-indicator"><i></i> LIVE</span>
        </div>
        <p class="guardrail-copy">직원의 전체 권한을 그대로 넘기지 않고, 현재 대출 Case에 필요한 범위만 Agent에 적용합니다.</p>

        <div class="permission-boundary">
          <div class="authority-scope">
            <span>직원 권한 상한</span>
            <strong>{{ workContext.employee.authorityScope }}</strong>
            <small>Employee Authority</small>
          </div>
          <div class="boundary-narrowing" aria-hidden="true"><span>∩</span><small>CASE BOUND</small></div>
          <div class="authority-scope effective">
            <span>Agent 현재 권한</span>
            <strong>{{ workContext.case.consumerId }} 전용</strong>
            <small>Effective Permission</small>
          </div>
        </div>

        <div class="passport-card">
          <div><span>Task Passport</span><strong>{{ workContext.passport.passportId }}</strong></div>
          <StatusBadge :value="workContext.passport.status" />
          <dl>
            <div><dt>Agent</dt><dd>{{ workContext.passport.agentId }}</dd></div>
            <div><dt>유효 시간</dt><dd>{{ workContext.passport.expiresAtLabel }}</dd></div>
          </dl>
        </div>

        <div class="tool-scope">
          <span>이 Case에서 허용된 Tool</span>
          <ul><li v-for="tool in workContext.passport.allowedTools" :key="tool"><i aria-hidden="true">✓</i>{{ tool }}</li></ul>
        </div>
        <p class="core-invariant">Agent Effective Permission <strong>⊆</strong> Employee Authority</p>
      </aside>
    </div>

    <article class="panel execution-panel" aria-live="polite">
      <div v-if="executionError" class="execution-error" role="alert">
        <span aria-hidden="true">!</span><div><h2>안전하게 실행을 중단했습니다</h2><p>{{ executionError }}</p></div>
      </div>
      <div v-else-if="!execution" class="execution-empty">
        <span class="pulse-ring" aria-hidden="true"><i></i></span>
        <div><p class="section-kicker">Waiting for agent activity</p><h2>Agent 실행 과정을 여기서 확인합니다</h2><p>업무를 요청하면 Tool Call 직전의 권한 검증과 금융 API 도달 여부가 표시됩니다.</p></div>
      </div>

      <template v-else>
        <div :class="['decision-banner', execution.decision.toLowerCase()]">
          <div class="decision-icon" aria-hidden="true">{{ execution.decision === 'ALLOW' ? '✓' : '!' }}</div>
          <div><p class="section-kicker">FinGuard policy decision</p><h2>{{ execution.title }}</h2><p>{{ execution.message }}</p></div>
          <StatusBadge :value="execution.decision" />
        </div>

        <div class="execution-content">
          <ol class="execution-timeline" aria-label="Agent 실행 단계">
            <li v-for="step in execution.steps" :key="step.id" :class="step.state.toLowerCase()">
              <span class="timeline-marker">{{ step.id }}</span>
              <div><small>{{ step.label }}</small><strong>{{ step.title }}</strong><p>{{ step.description }}</p></div>
              <StatusBadge :value="step.state" />
            </li>
          </ol>

          <aside class="decision-evidence">
            <p class="section-kicker">Decision evidence</p>
            <h3>{{ execution.requestId }}</h3>
            <dl>
              <div><dt>요청 고객</dt><dd>{{ execution.targetConsumerId }}</dd></div>
              <div><dt>현재 Case 고객</dt><dd>{{ workContext.case.consumerId }}</dd></div>
              <div><dt>요청 Tool</dt><dd>{{ execution.tool }}</dd></div>
              <div><dt>Customer scope</dt><dd><StatusBadge :value="execution.scopeStatus.customerScope" /></dd></div>
              <div><dt>Reason code</dt><dd class="reason-code">{{ execution.reasonCodes[0] || 'POLICY_REQUIREMENTS_MET' }}</dd></div>
              <div><dt>금융 API 도달</dt><dd>{{ execution.downstreamReached ? 'YES · 1회' : 'NO · 0회' }}</dd></div>
            </dl>
            <div :class="['downstream-state', { protected: !execution.downstreamReached }]">
              <span aria-hidden="true">{{ execution.downstreamReached ? '→' : '⊘' }}</span>
              <p><strong>{{ execution.downstreamReached ? 'Mock Finance 호출 완료' : 'Downstream 보호 완료' }}</strong>{{ execution.downstreamReached ? '허용된 요청만 금융 API에 전달했습니다.' : '차단된 요청은 금융 API에 전달되지 않았습니다.' }}</p>
            </div>
          </aside>
        </div>
      </template>
    </article>
  </section>
</template>
