<script setup>
defineProps({ value: { type: String, required: true } })
</script>

<template><span :class="['status-badge', `status-${value.toLowerCase()}`]">{{ value }}</span></template>
