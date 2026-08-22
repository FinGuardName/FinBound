<script setup>
defineProps({ label: { type: String, required: true }, value: { type: Number, required: true } })
</script>

<template>
  <div class="risk-meter">
    <div class="risk-label"><span>{{ label }}</span><strong>{{ value.toFixed(2) }}</strong></div>
    <div class="risk-track" aria-hidden="true"><span :style="{ width: `${Math.min(value * 100, 100)}%` }"></span></div>
  </div>
</template>
