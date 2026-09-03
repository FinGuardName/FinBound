<script setup>
import { computed, onMounted, ref, watch } from 'vue'

import RiskMeter from '../components/RiskMeter.vue'
import StatusBadge from '../components/StatusBadge.vue'
import { describeAuditReason } from '../presentation/auditReason'
import { finboundApi } from '../services/finboundApi'

const events = ref([])
const selectedId = ref(null)
const page = ref(1)
const pageSize = 5
const totalPages = ref(1)
const dashboardReady = ref(false)
const dashboardLoading = ref(false)
const dashboardError = ref('')
const summaryLoading = ref(false)
const summaryError = ref('')
const summary = ref(null)
const filterOptions = ref({ agentIds: [], caseIds: [], consumerIds: [], tools: [], reasonCodes: [] })
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

const decisionLabels = {
  ALLOW: '정상 처리',
  BLOCK: '차단',
  ERROR: '오류',
  PROCESSING: '처리 중',
  UNKNOWN: '확인 불가',
}
const severityLabels = { LOW: '일반', MEDIUM: '관찰', HIGH: '주의', CRITICAL: '긴급' }
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
const eventOutcome = (event) => {
  if (event.auditStatus === 'ERROR') return 'ERROR'
  if (event.auditStatus === 'PROCESSING') return 'PROCESSING'
  return event.decision ?? 'UNKNOWN'
}
const summaryMetric = (key) => summary.value?.[key] ?? '—'
const selectedEvent = computed(() => events.value.find((event) => event.auditEventId === selectedId.value))
const agentOptions = computed(() => filterOptions.value.agentIds)
const caseOptions = computed(() => filterOptions.value.caseIds)
const consumerOptions = computed(() => filterOptions.value.consumerIds)
const toolOptions = computed(() => filterOptions.value.tools)
const reasonOptions = computed(() => filterOptions.value.reasonCodes)
const capabilities = finboundApi.capabilities()

const promptStatusText = (event) => {
  if (event.promptEvaluationStatus === 'NOT_EVALUATED') return '미평가'
  if (event.promptInjectionDetected === null) return '탐지 결과 미제공'
  return event.promptInjectionDetected ? '위험 감지' : '위험 미감지'
}
const scopeStatusLabel = (value, event) => {
  if (value === 'OK') return '정상'
  if (value === 'VIOLATION') return '범위 초과'
  return event.auditStatus === 'PROCESSING' ? '확인 중' : '확인 불가'
}
const evidenceLabel = (value, event) => (
  value ?? (event.auditStatus === 'PROCESSING' ? '확인 중' : '확인 불가')
)
const versionLabel = (value, event, fallback = '미제공') => (
  value ?? (event.auditStatus === 'PROCESSING' ? '확인 중' : fallback)
)
const reasonCodeLabel = (event) => {
  if (event.reasonCodes[0]) return event.reasonCodes[0]
  if (event.auditStatus === 'PROCESSING') return '확인 중'
  if (event.decision === 'ALLOW' && event.systemOutcome === 'COMPLETED') return '차단 사유 없음'
  return '처리 사유 미제공'
}
const downstreamStatusLabel = (event) => {
  if (event.downstreamReached === true) return '요청 전달됨'
  if (event.downstreamReached === false) return '요청 전달 안 됨'
  return event.auditStatus === 'PROCESSING' ? '확인 중' : '확인 불가'
}
const responseStatusLabel = (event) => {
  if (event.responseReleased === true) return '제공함'
  if (event.responseReleased === false) return '제공 안 함'
  return event.auditStatus === 'PROCESSING' ? '확인 중' : '확인 불가'
}

async function loadEvents() {
  dashboardLoading.value = true
  dashboardError.value = ''
  try {
    const result = await finboundApi.getAuditEvents({
      filters: { ...filters.value },
      page: page.value,
      pageSize,
    })
    events.value = result.items
    totalPages.value = result.totalPages
    filterOptions.value = result.filterOptions
    selectedId.value = result.items[0]?.auditEventId ?? null
    if (selectedId.value) await selectEvent(selectedId.value)
  } catch {
    events.value = []
    selectedId.value = null
    dashboardError.value = '감사 기록을 불러오지 못했습니다. 연결 상태와 조회 권한을 확인해 주세요.'
  } finally {
    dashboardLoading.value = false
  }
}

async function loadSummary() {
  summaryLoading.value = true
  summaryError.value = ''
  try {
    summary.value = await finboundApi.getDashboardSummary()
  } catch {
    summary.value = null
    summaryError.value = '안전 현황 요약을 불러오지 못했습니다. 연결 상태와 조회 권한을 확인해 주세요.'
  } finally {
    summaryLoading.value = false
  }
}

async function loadDashboard() {
  await Promise.all([loadSummary(), loadEvents()])
  dashboardReady.value = true
}

async function selectEvent(auditEventId) {
  selectedId.value = auditEventId
  try {
    const detail = await finboundApi.getAuditEvent(auditEventId)
    const index = events.value.findIndex((event) => event.auditEventId === auditEventId)
    if (index >= 0) events.value[index] = detail
  } catch {
    dashboardError.value = '선택한 감사 기록의 상세 내용을 불러오지 못했습니다.'
  }
}

onMounted(loadDashboard)

watch(filters, () => {
  if (page.value === 1) loadEvents()
  else page.value = 1
}, { deep: true })

watch(page, () => {
  if (dashboardReady.value) loadEvents()
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
      <article><span>전체 업무</span><strong>{{ summaryMetric('total') }}</strong><small>{{ summaryLoading ? '요약 불러오는 중' : '최근 수집 기록' }}</small></article>
      <article><span>정상 처리</span><strong class="metric-allow">{{ summaryMetric('allow') }}</strong><small>업무 범위 안에서 완료</small></article>
      <article><span>안전 차단</span><strong class="metric-block">{{ summaryMetric('block') }}</strong><small>금융시스템 조회 전 중단</small></article>
      <article><span>처리 오류</span><strong class="metric-error">{{ summaryMetric('error') }}</strong><small>확인 또는 재처리 필요</small></article>
    </div>

    <div v-if="summaryError" class="dashboard-error" role="alert">
      <span>{{ summaryError }}</span><button type="button" @click="loadSummary">요약 다시 시도</button>
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
        <label class="check-label" :title="capabilities.riskOnlyFilter ? '' : 'Core API가 아직 이 필터를 제공하지 않습니다.'"><input v-model="filters.riskOnly" type="checkbox" :disabled="!capabilities.riskOnlyFilter" /> 확인이 필요한 항목만</label>
      </div>
      <details class="advanced-filters">
        <summary>상세 검색 조건</summary>
        <div class="advanced-filter-grid">
          <label>AI Agent<select v-model="filters.agentId" data-filter="agent"><option value="ALL">전체</option><option v-for="value in agentOptions" :key="value" :value="value">{{ value }}</option></select></label>
          <label>업무 건<select v-model="filters.caseId" data-filter="case"><option value="ALL">전체</option><option v-for="value in caseOptions" :key="value" :value="value">{{ value }}</option></select></label>
          <label>고객<select v-model="filters.consumerId" data-filter="consumer"><option value="ALL">전체</option><option v-for="value in consumerOptions" :key="value" :value="value">{{ consumerLabels[value] || value }}</option></select></label>
          <label>확인 업무<select v-model="filters.tool" data-filter="tool"><option value="ALL">전체</option><option v-for="value in toolOptions" :key="value" :value="value">{{ toolLabels[value] || value }}</option></select></label>
          <label :title="capabilities.severityFilter ? '' : 'Core API가 아직 이 필터를 제공하지 않습니다.'">중요도<select v-model="filters.severity" data-filter="severity" :disabled="!capabilities.severityFilter"><option value="ALL">전체</option><option value="LOW">일반</option><option value="MEDIUM">관찰</option><option value="HIGH">주의</option><option value="CRITICAL">긴급</option></select></label>
          <label>처리 사유<select v-model="filters.reasonCode" data-filter="reason"><option value="ALL">전체</option><option v-for="value in reasonOptions" :key="value" :value="value">{{ value }}</option></select></label>
        </div>
      </details>
    </section>

    <div v-if="dashboardError" class="dashboard-error" role="alert">
      <span>{{ dashboardError }}</span><button type="button" @click="loadEvents">목록 다시 시도</button>
    </div>

    <div class="dashboard-grid">
      <div class="panel event-list-panel">
        <div class="event-table" role="table" aria-label="AI 업무 처리 내역">
          <div class="table-head" role="row"><span>시간</span><span>고객 / 확인 업무</span><span>처리 결과</span></div>
          <button v-for="event in events" :key="event.auditEventId" :class="['event-row', { selected: selectedId === event.auditEventId }]" type="button" role="row" @click="selectEvent(event.auditEventId)">
            <span>{{ event.requestedAt.slice(11, 19) }}</span>
            <span><strong>{{ consumerLabels[event.targetConsumerId] || event.targetConsumerId }}</strong><small>{{ event.caseId }} · {{ toolLabels[event.requestedTool] || event.requestedTool }}</small></span>
            <StatusBadge :value="eventOutcome(event)" :label="decisionLabels[eventOutcome(event)]" />
          </button>
          <p v-if="dashboardLoading" class="no-results">업무 기록을 불러오는 중입니다.</p>
          <p v-else-if="!events.length" class="no-results">선택한 조건에 해당하는 업무가 없습니다.</p>
        </div>
        <nav v-if="events.length" class="pagination" aria-label="업무 기록 페이지">
          <button type="button" :disabled="page === 1" @click="page -= 1">이전</button>
          <span>{{ page }} / {{ totalPages }} 페이지</span>
          <button type="button" :disabled="page === totalPages" @click="page += 1">다음</button>
        </nav>
      </div>

      <aside v-if="selectedEvent" class="panel event-detail">
        <div class="panel-heading">
          <div><p class="section-kicker">선택한 업무 내역</p><h2>업무 내역 {{ selectedEvent.auditEventId.slice(-3) }}</h2></div>
          <StatusBadge :value="selectedEvent.severity" :label="severityLabels[selectedEvent.severity] || '미제공'" />
        </div>
        <RiskMeter
          label="입력 내용 주의도"
          :value="selectedEvent.promptRisk"
          :status-text="promptStatusText(selectedEvent)"
          :evaluation-status="selectedEvent.promptEvaluationStatus"
        />
        <RiskMeter label="AI 행동 주의도" :value="selectedEvent.behaviorRisk" :status-text="selectedEvent.behaviorRiskLevel" />
        <div class="detail-section">
          <p>업무 범위 확인</p>
          <div class="scope-list">
            <span v-for="(value, key) in selectedEvent.scopeStatus" :key="key">
              <small>{{ scopeLabels[key] }}</small>
              <StatusBadge :value="value" :label="scopeStatusLabel(value, selectedEvent)" />
            </span>
          </div>
        </div>
        <div class="detail-section reason-section">
          <p>처리 사유</p>
          <strong>{{ describeAuditReason(selectedEvent) }}</strong>
          <details><summary>시스템 처리 코드 보기</summary><small>{{ reasonCodeLabel(selectedEvent) }} · {{ selectedEvent.auditEventId }} · {{ selectedEvent.requestedTool }}</small></details>
        </div>
        <dl class="execution-state">
          <div><dt>금융시스템 요청</dt><dd>{{ downstreamStatusLabel(selectedEvent) }}</dd></div>
          <div><dt>결과 제공</dt><dd>{{ responseStatusLabel(selectedEvent) }}</dd></div>
        </dl>
        <details class="evidence-details">
          <summary>판단 근거와 버전 정보</summary>
          <dl>
            <div><dt>권한 판단</dt><dd>{{ evidenceLabel(selectedEvent.decision, selectedEvent) }}</dd></div>
            <div><dt>시스템 처리</dt><dd>{{ selectedEvent.auditStatus }}</dd></div>
            <div><dt>입력 평가</dt><dd>{{ selectedEvent.promptEvaluationStatus }}</dd></div>
            <div><dt>입력 모델</dt><dd>{{ selectedEvent.promptModelVersion || '미평가' }}</dd></div>
            <div><dt>행동 위험</dt><dd>{{ evidenceLabel(selectedEvent.behaviorRiskLevel === 'UNKNOWN' ? null : selectedEvent.behaviorRiskLevel, selectedEvent) }}</dd></div>
            <div><dt>특징 버전</dt><dd>{{ versionLabel(selectedEvent.featureVersion, selectedEvent) }}</dd></div>
            <div><dt>행동 모델</dt><dd>{{ versionLabel(selectedEvent.behaviorModelVersion, selectedEvent) }}</dd></div>
            <div><dt>정책 버전</dt><dd>{{ versionLabel(selectedEvent.policyVersion, selectedEvent, '확인 불가') }}</dd></div>
          </dl>
        </details>
      </aside>
    </div>
  </section>
</template>
