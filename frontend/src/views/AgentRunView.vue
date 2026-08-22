<script setup>
import { ref } from 'vue'
import StatusBadge from '../components/StatusBadge.vue'
import { finguardApi } from '../services/finguardApi'

const form = ref({ employeeId: 'EMP-101', consumerId: 'CUST-1001', taskType: 'LOAN_REVIEW' })
const agentRun = ref(null)
const loading = ref(false)
async function createRun() {
  loading.value = true
  agentRun.value = await finguardApi.createAgentRun(form.value)
  loading.value = false
}
</script>

<template>
  <section class="page-grid run-layout" aria-labelledby="run-heading">
    <div class="panel setup-panel">
      <div class="panel-heading"><div><p class="section-kicker">New delegation</p><h2 id="run-heading">Financial Case 생성</h2></div><span class="step-chip">STEP 1</span></div>
      <form @submit.prevent="createRun">
        <label>담당 직원<select v-model="form.employeeId"><option>EMP-101</option></select></label>
        <label>대상 소비자<select v-model="form.consumerId"><option>CUST-1001</option><option>CUST-9999</option></select></label>
        <label>업무 유형<select v-model="form.taskType"><option>LOAN_REVIEW</option></select></label>
        <div class="privacy-note"><strong>Data minimization</strong>원본 Prompt와 금융 Payload는 이 화면이나 Audit에 표시하지 않습니다.</div>
        <button class="primary-button" type="submit" :disabled="loading">{{ loading ? '권한 계산 중…' : 'AgentRun 생성' }}</button>
      </form>
    </div>
    <div class="panel result-panel" aria-live="polite">
      <div v-if="!agentRun" class="empty-state"><span class="empty-icon">↗</span><h2>Task Passport 대기 중</h2><p>Financial Case를 생성하면 Agent에게 위임된 최소 권한이 여기에 표시됩니다.</p></div>
      <template v-else>
        <div class="panel-heading"><div><p class="section-kicker">Effective permission</p><h2>{{ agentRun.passportId }}</h2></div><StatusBadge :value="agentRun.status" /></div>
        <dl class="data-grid"><div><dt>Agent Run</dt><dd>{{ agentRun.agentRunId }}</dd></div><div><dt>Financial Case</dt><dd>{{ agentRun.caseId }}</dd></div><div><dt>Consumer</dt><dd>{{ agentRun.consumerId }}</dd></div><div><dt>Expires</dt><dd>{{ agentRun.expiresAt.slice(11, 16) }} KST</dd></div></dl>
        <div class="permission-list"><p>허용된 Tool</p><div class="chip-row"><span v-for="tool in agentRun.allowedTools" :key="tool">{{ tool }}</span></div></div>
        <div class="invariant-callout"><span>Core invariant</span>Agent Effective Permission ⊆ Employee Authority</div>
      </template>
    </div>
  </section>
</template>
