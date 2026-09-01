<script setup>
import { computed } from 'vue'

const props = defineProps({
  label: { type: String, required: true },
  value: { type: Number, required: true },
  statusText: { type: String, required: true },
  evaluationStatus: { type: String, default: 'EVALUATED' },
})

const normalizedValue = computed(() => Math.min(Math.max(props.value, 0), 1))
const displayValue = computed(() => (
  props.evaluationStatus === 'NOT_EVALUATED'
    ? '미평가'
    : `${props.statusText} · 점수 ${normalizedValue.value.toFixed(2)}`
))
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
      :aria-valuenow="evaluationStatus === 'NOT_EVALUATED' ? undefined : normalizedValue"
      :aria-valuetext="displayValue"
    ><span :style="{ width: `${normalizedValue * 100}%` }"></span></div>
  </div>
</template>
