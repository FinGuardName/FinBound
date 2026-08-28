<script setup>
defineProps({
  value: { type: String, required: true },
  label: { type: String, default: '' },
})
</script>

<template><span :class="['status-badge', `status-${value.toLowerCase()}`]">{{ label || value }}</span></template>
