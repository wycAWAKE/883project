<template>
  <div class="app-shell">
    <a class="skip-link" href="#campus-content">Skip to content</a>
    <aside class="app-aside"><Aside /></aside>
    <div class="app-main">
      <Header />
      <main id="campus-content" class="app-content" tabindex="-1"><RouterView /></main>
    </div>
  </div>
</template>

<script setup>
import { RouterView } from 'vue-router'
import Header from '../components/Header.vue'
import Aside from '../components/Aside.vue'
</script>

<style scoped>
.app-shell { display: grid; grid-template-columns: 248px minmax(0, 1fr); height: 100dvh; background: #f5f7fb; overflow: hidden; }
.app-aside { min-height: 0; overflow-y: auto; border-right: 1px solid #e0e7ef; background: #fff; }
.app-main { display: flex; flex-direction: column; min-width: 0; min-height: 0; }
.app-content { flex: 1; min-height: 0; overflow: auto; scroll-behavior: auto; }
.skip-link { position: absolute; top: -100px; left: 16px; z-index: 100; padding: 12px; background: white; color: #245e9d; }
.skip-link:focus { top: 8px; }
@media (max-width: 900px) {
  .app-shell { grid-template-columns: minmax(0, 1fr); grid-template-rows: 60px minmax(0, 1fr); }
  .app-aside { grid-row: 1; overflow: hidden; border-right: 0; border-bottom: 1px solid #e0e7ef; }
  .app-main { grid-row: 2; }
}
</style>
