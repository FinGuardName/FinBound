<script setup>
import { computed, ref } from 'vue'

import AgentRunView from './views/AgentRunView.vue'
import DashboardView from './views/DashboardView.vue'

const screens = [
  { id: 'run', label: 'AI 업무 지원', kicker: '01' },
  { id: 'dashboard', label: 'AI 업무 안전 현황', kicker: '02' },
]
const activeScreen = ref('run')
const activeComponent = computed(() => ({
  run: AgentRunView,
  dashboard: DashboardView,
})[activeScreen.value])
</script>

<template>
  <div class="app-shell">
    <aside class="sidebar">
      <a class="brand" href="#main-content" aria-label="FinGuard 홈">
        <span class="brand-mark" aria-hidden="true">F</span>
        <span>FinGuard</span>
      </a>
      <nav aria-label="업무 메뉴">
        <button v-for="screen in screens" :key="screen.id" :data-screen="screen.id" :class="['nav-item', { active: activeScreen === screen.id }]" :aria-current="activeScreen === screen.id ? 'page' : undefined" type="button" @click="activeScreen = screen.id">
          <span>{{ screen.kicker }}</span>{{ screen.label }}
        </button>
      </nav>
      <div class="system-state"><span class="status-dot" aria-hidden="true"></span><div><strong>AI 업무 보호 적용 중</strong><small>현재 업무 범위로 제한</small></div></div>
    </aside>
    <main id="main-content">
      <header class="topbar">
        <div><p class="eyebrow">기업금융 여신 업무 시스템</p><h1>{{ screens.find((screen) => screen.id === activeScreen)?.label }}</h1></div>
        <div class="environment-badge"><span></span> AI 업무 보호 적용</div>
      </header>
      <component :is="activeComponent" />
    </main>
  </div>
</template>
