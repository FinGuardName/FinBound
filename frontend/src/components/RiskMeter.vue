<script setup>
import { computed } from 'vue'

const props = defineProps({
  label: { type: String, required: true },
  value: { type: Number, default: null },
  statusText: { type: String, required: true },
  evaluationStatus: { type: String, default: 'AUTO' },
})

const hasRiskValue = computed(() => Number.isFinite(props.value))
const hasKnownStatus = computed(() => Boolean(props.statusText) && props.statusText !== 'UNKNOWN')
const isEvaluated = computed(() => (
  props.evaluationStatus === 'EVALUATED'
    ? hasRiskValue.value && hasKnownStatus.value
    : props.evaluationStatus !== 'NOT_EVALUATED' && hasRiskValue.value && hasKnownStatus.value
))
const normalizedValue = computed(() => (
  hasRiskValue.value ? Math.min(Math.max(props.value, 0), 1) : null
))
const displayValue = computed(() => {
  if (isEvaluated.value) return `${props.statusText} · 점수 ${normalizedValue.value.toFixed(2)}`
  return props.evaluationStatus === 'NOT_EVALUATED' ? '미평가' : '확인 불가'
})
</script>

<template>
  <div class="risk-meter">
    <div class="risk-label"><span>{{ label }}</span><strong>{{ displayValue }}</strong></div>
    <div
      class="risk-track"
      role="meter"
      :aria-label="label"
      aria-valuemin="0"
      aria-valuemax="1"
      :aria-valuenow="isEvaluated ? normalizedValue : undefined"
      :aria-valuetext="displayValue"
    ><span :style="{ width: `${(normalizedValue ?? 0) * 100}%` }"></span></div>
  </div>
</template>
