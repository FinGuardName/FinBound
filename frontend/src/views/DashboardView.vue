<script setup>
import { computed, onMounted, ref, watch } from 'vue'

import RiskMeter from '../components/RiskMeter.vue'
import StatusBadge from '../components/StatusBadge.vue'
import { finguardApi } from '../services/finguardApi'

const events = ref([])
const selectedId = ref(null)
const page = ref(1)
const pageSize = 5
const filters = ref({
  period: '24H',
  agentId: 'ALL',
  caseId: 'ALL',
  consumerId: 'ALL',
  tool: 'ALL',
  outcome: 'ALL',
  severity: 'ALL',
  reasonCode: 'ALL',
  riskOnly: false,
})

const decisionLabels = { ALLOW: '정상 처리', BLOCK: '차단', ERROR: '오류' }
const severityLabels = { LOW: '일반', HIGH: '주의', CRITICAL: '긴급' }
const scopeLabels = {
  employeeAuthority: '담당 직원 권한',
  permissionTemplate: '업무 권한 기준',
  caseStatus: '업무 건 상태',
  mandate: '고객 동의',
  passportStatus: 'AI 업무 허가',
  agentBinding: 'AI 업무 연결',
  customerScope: '고객 범위',
  toolScope: '업무 범위',
  dataScope: '자료 범위',
}
const consumerLabels = {
  'CUST-1001': '김○○ 고객',
  'CUST-2001': '이○○ 고객',
  'CUST-2099': '최○○ 고객',
  'CUST-3001': '박○○ 고객',
  'CUST-9999': '정○○ 고객',
}
const toolLabels = {
  CREDIT_SCORE_READ: '신용정보 확인',
  INCOME_READ: '소득자료 확인',
  DEBT_READ: '부채자료 확인',
}
const reasonDescriptions = {
  CASE_SCOPE_VIOLATION: '현재 심사 건과 관련 없는 고객 자료가 포함되어 조회 전에 차단했습니다.',
  MANDATE_SCOPE_VIOLATION: '현재 고객 동의 범위에 포함되지 않은 자료라 조회 전에 차단했습니다.',
  PROMPT_INJECTION: 'AI 입력에서 업무 지시를 바꾸려는 위험 신호를 확인해 실행 전에 차단했습니다.',
  BEHAVIOR_ANOMALY: '평소 업무 흐름과 다른 AI 행동을 확인해 실행 전에 차단했습니다.',
  DOWNSTREAM_TIMEOUT: '금융시스템 응답이 지연되어 결과를 직원에게 제공하지 못했습니다.',
}

const eventOutcome = (event) => event.auditStatus === 'ERROR' ? 'ERROR' : event.decision
const uniqueValues = (key) => [...new Set(events.value.map((event) => event[key]))]
const agentOptions = computed(() => uniqueValues('agentId'))
const caseOptions = computed(() => uniqueValues('caseId'))
const consumerOptions = computed(() => uniqueValues('targetConsumerId'))
const toolOptions = computed(() => uniqueValues('requestedTool'))
const reasonOptions = computed(() => [...new Set(events.value.flatMap((event) => event.reasonCodes))])
const latestEventTime = computed(() => Math.max(...events.value.map((event) => new Date(event.requestedAt).getTime())))

const isInsidePeriod = (event) => {
  if (filters.value.period === 'ALL') return true
  const periodMs = filters.value.period === '30M' ? 30 * 60 * 1000 : 24 * 60 * 60 * 1000
  return latestEventTime.value - new Date(event.requestedAt).getTime() <= periodMs
}

const filteredEvents = computed(() => events.value.filter((event) => (
  isInsidePeriod(event)
  && (filters.value.agentId === 'ALL' || event.agentId === filters.value.agentId)
  && (filters.value.caseId === 'ALL' || event.caseId === filters.value.caseId)
  && (filters.value.consumerId === 'ALL' || event.targetConsumerId === filters.value.consumerId)
  && (filters.value.tool === 'ALL' || event.requestedTool === filters.value.tool)
  && (filters.value.outcome === 'ALL' || eventOutcome(event) === filters.value.outcome)
  && (filters.value.severity === 'ALL' || event.severity === filters.value.severity)
  && (filters.value.reasonCode === 'ALL' || event.reasonCodes.includes(filters.value.reasonCode))
  && (!filters.value.riskOnly || event.riskFlagged)
)))
const totalPages = computed(() => Math.max(1, Math.ceil(filteredEvents.value.length / pageSize)))
const pagedEvents = computed(() => filteredEvents.value.slice((page.value - 1) * pageSize, page.value * pageSize))
const selectedEvent = computed(() => events.value.find((event) => event.auditEventId === selectedId.value))
const summary = computed(() => ({
  total: events.value.length,
  allow: events.value.filter((event) => eventOutcome(event) === 'ALLOW').length,
  block: events.value.filter((event) => eventOutcome(event) === 'BLOCK').length,
  error: events.value.filter((event) => eventOutcome(event) === 'ERROR').length,
}))

onMounted(async () => {
  events.value = await finguardApi.getAuditEvents()
  selectedId.value = events.value[0]?.auditEventId
})

watch(filters, () => {
  page.value = 1
  selectedId.value = filteredEvents.value[0]?.auditEventId ?? null
}, { deep: true })

watch(page, () => {
  selectedId.value = pagedEvents.value[0]?.auditEventId ?? null
})
</script>

<template>
  <section class="dashboard-page" aria-labelledby="dashboard-heading">
    <div class="dashboard-intro">
      <div>
        <p class="section-kicker">최근 AI 업무 기록</p>
        <h2 id="dashboard-heading">AI 업무 안전 현황</h2>
        <p>직원이 요청한 업무가 정상 처리되었는지, 보호 설정이 필요한 순간에 작동했는지 확인합니다.</p>
      </div>
      <span class="dashboard-update"><i></i>방금 업데이트됨</span>
    </div>

    <div class="metric-grid">
      <article><span>전체 업무</span><strong>{{ summary.total }}</strong><small>최근 수집 기록</small></article>
      <article><span>정상 처리</span><strong class="metric-allow">{{ summary.allow }}</strong><small>업무 범위 안에서 완료</small></article>
      <article><span>안전 차단</span><strong class="metric-block">{{ summary.block }}</strong><small>금융시스템 조회 전 중단</small></article>
      <article><span>처리 오류</span><strong class="metric-error">{{ summary.error }}</strong><small>확인 또는 재처리 필요</small></article>
    </div>

    <section class="panel dashboard-filters" aria-label="업무 기록 검색 조건">
      <div class="filter-bar primary-filters">
        <label>
          조회 기간
          <select v-model="filters.period" data-filter="period">
            <option value="30M">최근 30분</option>
            <option value="24H">최근 24시간</option>
            <option value="ALL">전체 기록</option>
          </select>
        </label>
        <label>
          처리 결과
          <select v-model="filters.outcome" data-filter="outcome">
            <option value="ALL">전체</option>
            <option value="ALLOW">정상 처리</option>
            <option value="BLOCK">차단</option>
            <option value="ERROR">오류</option>
          </select>
        </label>
        <label class="check-label"><input v-model="filters.riskOnly" type="checkbox" /> 확인이 필요한 항목만</label>
      </div>
      <details class="advanced-filters">
        <summary>상세 검색 조건</summary>
        <div class="advanced-filter-grid">
          <label>AI Agent<select v-model="filters.agentId" data-filter="agent"><option value="ALL">전체</option><option v-for="value in agentOptions" :key="value" :value="value">{{ value }}</option></select></label>
          <label>업무 건<select v-model="filters.caseId" data-filter="case"><option value="ALL">전체</option><option v-for="value in caseOptions" :key="value" :value="value">{{ value }}</option></select></label>
          <label>고객<select v-model="filters.consumerId" data-filter="consumer"><option value="ALL">전체</option><option v-for="value in consumerOptions" :key="value" :value="value">{{ consumerLabels[value] || value }}</option></select></label>
          <label>확인 업무<select v-model="filters.tool" data-filter="tool"><option value="ALL">전체</option><option v-for="value in toolOptions" :key="value" :value="value">{{ toolLabels[value] || value }}</option></select></label>
          <label>중요도<select v-model="filters.severity" data-filter="severity"><option value="ALL">전체</option><option value="LOW">일반</option><option value="HIGH">주의</option><option value="CRITICAL">긴급</option></select></label>
          <label>처리 사유<select v-model="filters.reasonCode" data-filter="reason"><option value="ALL">전체</option><option v-for="value in reasonOptions" :key="value" :value="value">{{ value }}</option></select></label>
        </div>
      </details>
    </section>

    <div class="dashboard-grid">
      <div class="panel event-list-panel">
        <div class="event-table" role="table" aria-label="AI 업무 처리 내역">
          <div class="table-head" role="row"><span>시간</span><span>고객 / 확인 업무</span><span>처리 결과</span></div>
          <button v-for="event in pagedEvents" :key="event.auditEventId" :class="['event-row', { selected: selectedId === event.auditEventId }]" type="button" role="row" @click="selectedId = event.auditEventId">
            <span>{{ event.requestedAt.slice(11, 19) }}</span>
            <span><strong>{{ consumerLabels[event.targetConsumerId] || event.targetConsumerId }}</strong><small>{{ event.caseId }} · {{ toolLabels[event.requestedTool] || event.requestedTool }}</small></span>
            <StatusBadge :value="eventOutcome(event)" :label="decisionLabels[eventOutcome(event)]" />
          </button>
          <p v-if="!filteredEvents.length" class="no-results">선택한 조건에 해당하는 업무가 없습니다.</p>
        </div>
        <nav v-if="filteredEvents.length" class="pagination" aria-label="업무 기록 페이지">
          <button type="button" :disabled="page === 1" @click="page -= 1">이전</button>
          <span>{{ page }} / {{ totalPages }} 페이지</span>
          <button type="button" :disabled="page === totalPages" @click="page += 1">다음</button>
        </nav>
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
          <div class="scope-list">
            <span v-for="(value, key) in selectedEvent.scopeStatus" :key="key">
              <small>{{ scopeLabels[key] }}</small>
              <StatusBadge :value="value" :label="value === 'OK' ? '정상' : '범위 초과'" />
            </span>
          </div>
        </div>
        <div class="detail-section reason-section">
          <p>처리 사유</p>
          <strong>{{ reasonDescriptions[selectedEvent.reasonCodes[0]] || '요청한 업무 범위 안에서 정상 처리했습니다.' }}</strong>
          <details><summary>시스템 처리 코드 보기</summary><small>{{ selectedEvent.reasonCodes[0] || '차단 사유 없음' }} · {{ selectedEvent.auditEventId }} · {{ selectedEvent.requestedTool }}</small></details>
        </div>
        <dl class="execution-state">
          <div><dt>금융시스템 조회</dt><dd>{{ selectedEvent.downstreamReached ? '조회함' : '조회 안 함' }}</dd></div>
          <div><dt>결과 제공</dt><dd>{{ selectedEvent.responseReleased ? '제공함' : '제공 안 함' }}</dd></div>
        </dl>
        <details class="evidence-details">
          <summary>판단 근거와 버전 정보</summary>
          <dl>
            <div><dt>권한 판단</dt><dd>{{ selectedEvent.decision }}</dd></div>
            <div><dt>시스템 처리</dt><dd>{{ selectedEvent.auditStatus }}</dd></div>
            <div><dt>입력 평가</dt><dd>{{ selectedEvent.promptEvaluationStatus }}</dd></div>
            <div><dt>입력 모델</dt><dd>{{ selectedEvent.promptModelVersion }}</dd></div>
            <div><dt>행동 위험</dt><dd>{{ selectedEvent.behaviorRiskLevel }}</dd></div>
            <div><dt>특징 버전</dt><dd>{{ selectedEvent.featureVersion }}</dd></div>
            <div><dt>행동 모델</dt><dd>{{ selectedEvent.behaviorModelVersion }}</dd></div>
            <div><dt>정책 버전</dt><dd>{{ selectedEvent.policyVersion }}</dd></div>
          </dl>
        </details>
      </aside>
    </div>
  </section>
</template>
