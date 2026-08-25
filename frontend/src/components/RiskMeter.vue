<script setup>
import { computed } from 'vue'

const props = defineProps({ label: { type: String, required: true }, value: { type: Number, required: true } })
const percent = computed(() => Math.round(props.value * 100))
const level = computed(() => {
  if (props.value >= 0.8) return '높음'
  if (props.value >= 0.4) return '주의'
  return '낮음'
})
</script>

<template>
  <div class="risk-meter">
    <div class="risk-label"><span>{{ label }}</span><strong>{{ level }} · {{ percent }}%</strong></div>
    <div class="risk-track" aria-hidden="true"><span :style="{ width: `${Math.min(value * 100, 100)}%` }"></span></div>
  </div>
</template>
