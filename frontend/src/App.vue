<script setup>
const screens = [
  ['1', 'LoanAgent 실행', 'Financial Case와 Task Passport를 생성하고 Tool Call을 실행합니다.'],
  ['2', '권한 비교', 'Employee Authority와 Agent Effective Permission을 나란히 확인합니다.'],
  ['3', 'Security Dashboard', 'ALLOW, BLOCK, ERROR와 Scope·AI·정책 근거를 조회합니다.'],
]
</script>

<template>
  <main>
    <p class="eyebrow">RUNTIME AUTHORIZATION GATEWAY</p>
    <h1>FinGuard</h1>
    <p class="lead">사람이 할 수 있다고 해서, AI Agent가 지금 그 일을 해도 되는 것은 아닙니다.</p>

    <section aria-labelledby="screens-title">
      <h2 id="screens-title">P0 화면</h2>
      <div class="grid">
        <article v-for="screen in screens" :key="screen[0]">
          <span>{{ screen[0] }}</span>
          <h3>{{ screen[1] }}</h3>
          <p>{{ screen[2] }}</p>
        </article>
      </div>
    </section>
  </main>
</template>
