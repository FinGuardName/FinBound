<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import RiskMeter from '../components/RiskMeter.vue'
import StatusBadge from '../components/StatusBadge.vue'
import { finguardApi } from '../services/finguardApi'

const events = ref([])
const selectedId = ref(null)
const decisionFilter = ref('ALL')
const riskOnly = ref(false)
onMounted(async () => { events.value = await finguardApi.getAuditEvents(); selectedId.value = events.value[0]?.auditEventId })
const filteredEvents = computed(() => events.value.filter((event) => (decisionFilter.value === 'ALL' || event.decision === decisionFilter.value) && (!riskOnly.value || event.riskFlagged)))
const selectedEvent = computed(() => events.value.find((event) => event.auditEventId === selectedId.value))
const summary = computed(() => ({ total: events.value.length, allow: events.value.filter((event) => event.decision === 'ALLOW').length, block: events.value.filter((event) => event.decision === 'BLOCK').length, error: events.value.filter((event) => event.decision === 'ERROR').length }))
watch([decisionFilter, riskOnly], () => { selectedId.value = filteredEvents.value[0]?.auditEventId ?? null })
</script>

<template>
  <section class="dashboard-page" aria-labelledby="dashboard-heading">
    <h2 id="dashboard-heading" class="sr-only">Security Event Dashboard</h2>
    <div class="metric-grid"><article><span>Total events</span><strong>{{ summary.total }}</strong><small>Last 24 hours</small></article><article><span>Allowed</span><strong class="metric-allow">{{ summary.allow }}</strong><small>Policy passed</small></article><article><span>Blocked</span><strong class="metric-block">{{ summary.block }}</strong><small>Downstream protected</small></article><article><span>Errors</span><strong class="metric-error">{{ summary.error }}</strong><small>System outcome</small></article></div>
    <div class="dashboard-grid">
      <div class="panel event-list-panel"><div class="filter-bar"><label>Decision<select v-model="decisionFilter"><option>ALL</option><option>ALLOW</option><option>BLOCK</option><option>ERROR</option></select></label><label class="check-label"><input v-model="riskOnly" type="checkbox" /> 위험 이벤트만</label></div><div class="event-table" role="table" aria-label="Audit events"><div class="table-head" role="row"><span>Time</span><span>Target / Tool</span><span>Decision</span></div><button v-for="event in filteredEvents" :key="event.auditEventId" :class="['event-row', { selected: selectedId === event.auditEventId }]" type="button" role="row" @click="selectedId = event.auditEventId"><span>{{ event.requestedAt.slice(11, 19) }}</span><span><strong>{{ event.targetConsumerId }}</strong><small>{{ event.tool }}</small></span><StatusBadge :value="event.decision" /></button><p v-if="!filteredEvents.length" class="no-results">조건에 맞는 이벤트가 없습니다.</p></div></div>
      <aside v-if="selectedEvent" class="panel event-detail"><div class="panel-heading"><div><p class="section-kicker">Decision evidence</p><h2>{{ selectedEvent.auditEventId }}</h2></div><StatusBadge :value="selectedEvent.severity" /></div><RiskMeter label="Prompt risk" :value="selectedEvent.promptRisk" /><RiskMeter label="Behavior risk" :value="selectedEvent.behaviorRisk" /><div class="detail-section"><p>Scope status</p><div class="scope-list"><span v-for="(value, key) in selectedEvent.scopeStatus" :key="key"><small>{{ key }}</small><StatusBadge :value="value" /></span></div></div><div class="detail-section"><p>Reason code</p><strong class="reason-code">{{ selectedEvent.reasonCodes[0] || 'POLICY_REQUIREMENTS_MET' }}</strong></div><dl class="execution-state"><div><dt>Downstream reached</dt><dd>{{ selectedEvent.downstreamReached ? 'YES' : 'NO' }}</dd></div><div><dt>Response released</dt><dd>{{ selectedEvent.responseReleased ? 'YES' : 'NO' }}</dd></div></dl></aside>
    </div>
  </section>
</template>
