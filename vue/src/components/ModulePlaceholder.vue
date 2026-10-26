<template>
  <section class="module-page" :aria-labelledby="`${module.id}-title`">
    <RouterLink to="/home" class="back-link"><span aria-hidden="true">←</span> Back to home</RouterLink>
    <div class="page-heading">
      <span class="page-icon" aria-hidden="true"><component :is="module.icon" /></span>
      <div>
        <p class="module-category">{{ module.group }} / {{ module.id }}</p>
        <h1 :id="`${module.id}-title`">{{ module.name }}</h1>
      </div>
    </div>
    <p class="page-description">{{ module.description }}</p>
    <div class="availability-panel">
      <span class="availability-icon" aria-hidden="true"><Clock /></span>
      <h2>{{ module.status }}</h2>
      <p>This service is being prepared and is not available yet.</p>
      <p>You can explore the other campus services from the home page.</p>
      <RouterLink to="/home" class="home-button">Explore campus services</RouterLink>
    </div>
  </section>
</template>

<script setup>
import { RouterLink } from 'vue-router'
import { Clock } from '@element-plus/icons-vue'
defineProps({ module: { type: Object, required: true } })
</script>

<style scoped>
.module-page { max-width: 1100px; margin: 0 auto; padding: 36px; }
.back-link { display: inline-flex; gap: 8px; color: #526c88; font-size: 13px; text-decoration: none; }
.back-link:hover { text-decoration: underline; }
.page-heading { display: flex; align-items: center; gap: 18px; margin-top: 32px; }
.page-icon { display: grid; place-items: center; flex-shrink: 0; width: 58px; height: 58px; border-radius: 16px; color: #346bb9; background: #eaf2ff; }
.page-icon svg { width: 28px; height: 28px; }
.module-category { margin-bottom: 7px; color: #6c7c8f; font-size: 12px; }
h1 { font-size: clamp(23px, 3vw, 32px); letter-spacing: -.6px; line-height: 1.3; }
.page-description { margin-top: 20px; color: #61738a; line-height: 1.8; }
.availability-panel { margin-top: 32px; padding: 58px 24px; border: 1px solid #dfe7ef; border-radius: 16px; background: #fff; text-align: center; }
.availability-icon { display: inline-flex; padding: 15px; border-radius: 50%; color: #99762f; background: #fbf4e6; }
.availability-icon svg { width: 28px; height: 28px; }
h2 { margin-top: 22px; margin-bottom: 14px; font-size: 23px; }
.availability-panel p { color: #65758a; line-height: 1.9; font-size: 14px; }
.home-button { display: inline-block; margin-top: 28px; padding: 12px 20px; border-radius: 8px; background: #2e68b3; color: #fff; font-size: 13px; font-weight: 600; text-decoration: none; }
a:focus-visible { outline: 3px solid #346bb9; outline-offset: 4px; }
@media (max-width: 600px) { .module-page { padding: 24px 18px; } .page-heading { gap: 12px; } .availability-panel { padding: 40px 18px; } }
</style>
