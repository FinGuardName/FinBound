<script setup>
import { computed, ref } from 'vue'

import brandLogoDark from './assets/finbound-logo-dark.png'
import AgentRunView from './views/AgentRunView.vue'
import DashboardView from './views/DashboardView.vue'
import { finboundApi } from './services/finboundApi'

const screens = [
  { id: 'run', label: 'AI 업무 지원', kicker: '01' },
  { id: 'dashboard', label: 'AI 업무 안전 현황', kicker: '02' },
]
const activeScreen = ref('run')
const realMode = finboundApi.isRealMode()
const credential = ref('')
const sessionReady = ref(!realMode || finboundApi.hasCredential())
const activeComponent = computed(() => ({
  run: AgentRunView,
  dashboard: DashboardView,
})[activeScreen.value])

function startSession() {
  if (!credential.value.trim()) return
  finboundApi.setCredential(credential.value.trim())
  credential.value = ''
  sessionReady.value = true
}

function endSession() {
  finboundApi.clearCredential()
  sessionReady.value = false
  activeScreen.value = 'run'
}
</script>

<template>
  <div class="app-shell">
    <aside class="sidebar">
      <a class="brand" href="#main-content" aria-label="FinBound 홈">
        <span class="brand-mark" aria-hidden="true"><img :src="brandLogoDark" alt="" /></span>
        <span>FinBound</span>
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
        <div class="runtime-actions">
          <div class="environment-badge"><span></span> {{ realMode ? 'Core API 연결 모드' : 'Mock 검증 모드' }}</div>
          <button v-if="realMode && sessionReady" class="session-end" type="button" @click="endSession">연결 종료</button>
        </div>
      </header>
      <section v-if="realMode && !sessionReady" class="panel credential-panel" aria-labelledby="credential-heading">
        <p class="section-kicker">보호된 Core API 연결</p>
        <h2 id="credential-heading">업무 세션 Credential을 입력해 주세요</h2>
        <p>Credential은 현재 브라우저 메모리에서만 사용하며 Web Storage나 빌드 파일에 저장하지 않습니다.</p>
        <form @submit.prevent="startSession">
          <label for="core-credential">Operator 또는 Viewer Credential</label>
          <div>
            <input id="core-credential" v-model="credential" type="password" autocomplete="off" spellcheck="false" required />
            <button class="primary-button" type="submit">Core API 연결</button>
          </div>
        </form>
      </section>
      <component v-else :is="activeComponent" />
    </main>
  </div>
</template>
