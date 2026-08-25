<script setup>
import { computed, onMounted, ref, watch } from 'vue'

import RiskMeter from '../components/RiskMeter.vue'
import StatusBadge from '../components/StatusBadge.vue'
import { finguardApi } from '../services/finguardApi'

const events = ref([])
const selectedId = ref(null)
const decisionFilter = ref('ALL')
const riskOnly = ref(false)

const decisionLabels = { ALLOW: '정상 처리', BLOCK: '차단', ERROR: '오류' }
const severityLabels = { LOW: '일반', HIGH: '주의', CRITICAL: '긴급' }
const scopeLabels = {
  customerScope: '고객 범위',
  toolScope: '업무 범위',
  dataScope: '자료 범위',
  mandate: '고객 동의',
}
const consumerLabels = { 'CUST-1001': '김○○ 고객', 'CUST-9999': '박○○ 고객' }
const toolLabels = {
  CREDIT_SCORE_READ: '신용정보 확인',
  INCOME_READ: '소득자료 확인',
  DEBT_READ: '부채자료 확인',
}
const reasonDescriptions = {
  CASE_SCOPE_VIOLATION: '현재 대출 신청 건과 관련 없는 고객 자료가 포함되었습니다.',
  DOWNSTREAM_TIMEOUT: '금융시스템 응답이 지연되어 결과를 제공하지 못했습니다.',
}

onMounted(async () => {
  events.value = await finguardApi.getAuditEvents()
  selectedId.value = events.value[0]?.auditEventId
})

const filteredEvents = computed(() => events.value.filter((event) =>
  (decisionFilter.value === 'ALL' || event.decision === decisionFilter.value)
  && (!riskOnly.value || event.riskFlagged),
))
const selectedEvent = computed(() => events.value.find((event) => event.auditEventId === selectedId.value))
const summary = computed(() => ({
  total: events.value.length,
  allow: events.value.filter((event) => event.decision === 'ALLOW').length,
  block: events.value.filter((event) => event.decision === 'BLOCK').length,
  error: events.value.filter((event) => event.decision === 'ERROR').length,
}))

watch([decisionFilter, riskOnly], () => {
  selectedId.value = filteredEvents.value[0]?.auditEventId ?? null
})
</script>

<template>
  <section class="dashboard-page" aria-labelledby="dashboard-heading">
    <div class="dashboard-intro">
      <div><p class="section-kicker">최근 24시간</p><h2 id="dashboard-heading">AI 업무 처리 현황</h2><p>AI가 수행한 업무와 보호 설정이 작동한 내역을 확인합니다.</p></div>
      <span class="dashboard-update"><i></i>방금 업데이트됨</span>
    </div>

    <div class="metric-grid">
      <article><span>전체 업무</span><strong>{{ summary.total }}</strong><small>최근 24시간</small></article>
      <article><span>정상 처리</span><strong class="metric-allow">{{ summary.allow }}</strong><small>업무 범위 안에서 완료</small></article>
      <article><span>안전 차단</span><strong class="metric-block">{{ summary.block }}</strong><small>자료 조회 전에 중단</small></article>
      <article><span>처리 오류</span><strong class="metric-error">{{ summary.error }}</strong><small>확인 또는 재처리 필요</small></article>
    </div>

    <div class="dashboard-grid">
      <div class="panel event-list-panel">
        <div class="filter-bar">
          <label>처리 결과<select v-model="decisionFilter"><option value="ALL">전체</option><option value="ALLOW">정상 처리</option><option value="BLOCK">차단</option><option value="ERROR">오류</option></select></label>
          <label class="check-label"><input v-model="riskOnly" type="checkbox" /> 확인이 필요한 항목만</label>
        </div>
        <div class="event-table" role="table" aria-label="AI 업무 처리 내역">
          <div class="table-head" role="row"><span>시간</span><span>고객 / 확인 업무</span><span>처리 결과</span></div>
          <button v-for="event in filteredEvents" :key="event.auditEventId" :class="['event-row', { selected: selectedId === event.auditEventId }]" type="button" role="row" @click="selectedId = event.auditEventId">
            <span>{{ event.requestedAt.slice(11, 19) }}</span>
            <span><strong>{{ consumerLabels[event.targetConsumerId] }}</strong><small>{{ event.targetConsumerId }} · {{ toolLabels[event.tool] }}</small></span>
            <StatusBadge :value="event.decision" :label="decisionLabels[event.decision]" />
          </button>
          <p v-if="!filteredEvents.length" class="no-results">선택한 조건에 해당하는 업무가 없습니다.</p>
        </div>
      </div>

      <aside v-if="selectedEvent" class="panel event-detail">
        <div class="panel-heading">
          <div><p class="section-kicker">선택한 업무 내역</p><h2>업무 내역 {{ selectedEvent.auditEventId.slice(-3) }}</h2></div>
          <StatusBadge :value="selectedEvent.severity" :label="severityLabels[selectedEvent.severity]" />
        </div>
        <RiskMeter label="입력 내용 주의도" :value="selectedEvent.promptRisk" />
        <RiskMeter label="AI 행동 주의도" :value="selectedEvent.behaviorRisk" />
        <div class="detail-section">
          <p>업무 범위 확인</p>
          <div class="scope-list"><span v-for="(value, key) in selectedEvent.scopeStatus" :key="key"><small>{{ scopeLabels[key] }}</small><StatusBadge :value="value" :label="value === 'OK' ? '정상' : '범위 초과'" /></span></div>
        </div>
        <div class="detail-section reason-section">
          <p>처리 사유</p>
          <strong>{{ reasonDescriptions[selectedEvent.reasonCodes[0]] || '모든 보호 조건을 충족했습니다.' }}</strong>
          <details><summary>시스템 처리 코드 보기</summary><small>{{ selectedEvent.reasonCodes[0] || 'POLICY_REQUIREMENTS_MET' }} · {{ selectedEvent.auditEventId }} · {{ selectedEvent.tool }}</small></details>
        </div>
        <dl class="execution-state">
          <div><dt>금융시스템 조회</dt><dd>{{ selectedEvent.downstreamReached ? '조회함' : '조회 안 함' }}</dd></div>
          <div><dt>결과 제공</dt><dd>{{ selectedEvent.responseReleased ? '제공함' : '제공 안 함' }}</dd></div>
        </dl>
      </aside>
    </div>
  </section>
</template>
