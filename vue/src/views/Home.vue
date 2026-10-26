<template>
  <div class="campus-home">
    <section class="welcome-panel" aria-labelledby="welcome-title">
      <div>
        <p class="eyebrow">SMARTCAMPUS / SERVICE DIRECTORY</p>
        <h1 id="welcome-title">Your campus, connected.</h1>
        <p class="welcome-description">One place for learning, facilities and administration.</p>
      </div>
      <span class="welcome-mark" aria-hidden="true"><School /></span>
    </section>
    <section aria-labelledby="services-title">
      <div class="section-heading">
        <div>
          <h2 id="services-title">Explore campus services</h2>
          <p>Choose a service to see its purpose and availability.</p>
        </div>
        <span class="directory-count">6 service modules</span>
      </div>
      <div class="module-grid">
        <RouterLink v-for="module in campusModules" :key="module.id" :to="module.path"
                    class="module-card" :class="module.group.toLowerCase()">
          <div class="card-top">
            <span class="module-icon" aria-hidden="true"><component :is="module.icon" /></span>
            <span class="module-id">{{ module.id }} / {{ module.group }}</span>
          </div>
          <h3>{{ module.name }}</h3>
          <p class="module-description">{{ module.description }}</p>
          <div class="card-bottom">
            <span class="module-status"><span aria-hidden="true"></span>{{ module.status }}</span>
            <span class="open-label">Explore <span aria-hidden="true">↗</span></span>
          </div>
        </RouterLink>
      </div>
    </section>
    <p class="portal-note">Services are being prepared. Each module shows its current availability.</p>
  </div>
</template>

<script setup>
import { RouterLink } from 'vue-router'
import { School } from '@element-plus/icons-vue'
import { campusModules } from '../config/modules.js'
</script>

<style scoped>
.campus-home { max-width: 1440px; margin: 0 auto; padding: 32px; }
.welcome-panel { display: flex; align-items: center; justify-content: space-between; gap: 24px; padding: 38px 40px; border: 1px solid #d7e6f6; border-radius: 18px; background: linear-gradient(115deg, #eaf3ff, #f0faf7); }
.eyebrow { margin-bottom: 15px; color: #375e88; font-size: 11px; font-weight: 800; letter-spacing: 1.8px; }
h1 { color: #142e4b; font-size: clamp(28px, 3vw, 40px); font-weight: 750; line-height: 1.2; letter-spacing: -1.2px; }
.welcome-description { margin-top: 14px; color: #506880; line-height: 1.7; }
.welcome-mark { display: grid; place-items: center; flex-shrink: 0; width: 92px; height: 92px; border: 1px solid #cfe5e2; border-radius: 24px; color: #378b84; background: #ffffffa6; transform: rotate(-7deg); }
.welcome-mark svg { width: 48px; height: 48px; }
.section-heading { display: flex; align-items: center; justify-content: space-between; gap: 18px; margin: 32px 0 22px; }
h2 { font-size: 21px; letter-spacing: -.4px; }
.section-heading p { margin-top: 7px; color: #69788f; font-size: 14px; line-height: 1.6; }
.directory-count { flex-shrink: 0; color: #65768a; font-size: 12px; }
.module-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 20px; }
.module-card { --module-color: #346bb9; --module-soft: #eaf2ff; display: flex; flex-direction: column; min-width: 0; padding: 25px; border: 1px solid #dfe7ef; border-radius: 14px; background: #fff; color: inherit; text-decoration: none; box-shadow: 0 4px 16px #1f3e6010; transition: transform .18s, box-shadow .18s, border-color .18s; }
.facilities { --module-color: #23786e; --module-soft: #e8f6f1; }
.administration { --module-color: #7d59a8; --module-soft: #f2edfa; }
.module-card:hover { transform: translateY(-3px); border-color: var(--module-color); box-shadow: 0 10px 25px #1f3e6016; }
.module-card:focus-visible { outline: 3px solid var(--module-color); outline-offset: 4px; }
.card-top { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 23px; }
.module-icon { display: grid; place-items: center; width: 46px; height: 46px; flex-shrink: 0; border-radius: 12px; color: var(--module-color); background: var(--module-soft); }
.module-icon svg { width: 24px; height: 24px; }
.module-id { color: #6c7c8f; font-size: 11px; text-align: right; }
h3 { font-size: 18px; line-height: 1.4; letter-spacing: -.3px; }
.module-description { flex: 1; margin-top: 12px; margin-bottom: 26px; color: #65758a; font-size: 13px; line-height: 1.8; }
.card-bottom { display: flex; align-items: center; justify-content: space-between; gap: 10px; padding-top: 18px; border-top: 1px solid #edf1f6; }
.module-status { display: inline-flex; align-items: center; gap: 7px; color: #7d642c; font-size: 11px; }
.module-status > span { width: 6px; height: 6px; border-radius: 50%; background: #bc933f; }
.open-label { color: var(--module-color); font-size: 12px; font-weight: 700; white-space: nowrap; }
.open-label > span { margin-left: 5px; }
.portal-note { margin-top: 25px; color: #6f7f92; font-size: 12px; text-align: center; line-height: 1.7; }
@media (max-width: 1150px) { .module-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 600px) {
  .campus-home { padding: 20px 16px; }
  .welcome-panel { padding: 28px 22px; }
  .welcome-mark { display: none; }
  .eyebrow { font-size: 10px; letter-spacing: 1px; }
  .module-grid { grid-template-columns: minmax(0, 1fr); gap: 16px; }
  .directory-count { display: none; }
  .module-card { padding: 23px; }
}
@media (prefers-reduced-motion: reduce) { .module-card { transition: none; } .module-card:hover { transform: none; } }
</style>
