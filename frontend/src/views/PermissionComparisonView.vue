<script setup>
import { onMounted, ref } from 'vue'
import StatusBadge from '../components/StatusBadge.vue'
import { finguardApi } from '../services/finguardApi'

const comparison = ref(null)
onMounted(async () => { comparison.value = await finguardApi.getPermissionComparison() })
</script>

<template>
  <section v-if="comparison" class="comparison-page" aria-labelledby="comparison-heading">
    <div class="comparison-summary"><div><p class="section-kicker">Least privilege proof</p><h2 id="comparison-heading">넓은 직원 권한을 Case 단위로 축소합니다</h2><p>Agent 권한은 직원 권한을 넘지 않으며 현재 소비자와 유효시간에 묶입니다.</p></div><div class="subset-formula"><span>AGENT</span> ⊆ <span>EMPLOYEE</span></div></div>
    <div class="compare-grid">
      <article class="panel authority-card"><div class="panel-heading"><div><p class="section-kicker">Authority ceiling</p><h2>{{ comparison.employee.id }}</h2></div><StatusBadge :value="comparison.employee.status" /></div><p class="scope-label">Customer scope</p><strong class="scope-value">{{ comparison.employee.customerScope }}</strong><div class="permission-list"><p>Tools</p><div class="chip-row"><span v-for="tool in comparison.employee.tools" :key="tool">{{ tool }}</span></div></div><div class="permission-list"><p>Data</p><div class="chip-row"><span v-for="data in comparison.employee.data" :key="data">{{ data }}</span></div></div></article>
      <div class="narrowing-arrow" aria-label="권한 축소">→<small>CASE BOUND</small></div>
      <article class="panel authority-card effective-card"><div class="panel-heading"><div><p class="section-kicker">Effective permission</p><h2>{{ comparison.agent.id }}</h2></div><StatusBadge :value="comparison.agent.status" /></div><p class="scope-label">Customer scope</p><strong class="scope-value accent">{{ comparison.agent.customerScope }}</strong><div class="permission-list"><p>Tools</p><div class="chip-row"><span v-for="tool in comparison.agent.tools" :key="tool">{{ tool }}</span></div></div><dl class="passport-meta"><div><dt>Passport</dt><dd>{{ comparison.agent.passportId }}</dd></div><div><dt>Expires</dt><dd>{{ comparison.agent.expiresAt.slice(11, 16) }} KST</dd></div></dl></article>
    </div>
  </section>
</template>
