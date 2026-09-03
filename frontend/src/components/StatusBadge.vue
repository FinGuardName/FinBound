<script setup>
import { computed } from 'vue'

const props = defineProps({
  value: { type: String, default: 'UNKNOWN' },
  label: { type: String, default: '' },
})

const normalizedValue = computed(() => (
  typeof props.value === 'string' && props.value.trim() ? props.value : 'UNKNOWN'
))
const displayLabel = computed(() => props.label || normalizedValue.value)
</script>

<template><span :class="['status-badge', `status-${normalizedValue.toLowerCase()}`]">{{ displayLabel }}</span></template>
