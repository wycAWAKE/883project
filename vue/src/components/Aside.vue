<template>
  <div class="campus-sidebar">
    <RouterLink to="/home" class="campus-brand" aria-label="SmartCampus home">
      <span class="brand-icon" aria-hidden="true"><School /></span>
      <span><strong>SmartCampus</strong><small>Integrated services</small></span>
    </RouterLink>
    <nav aria-label="Campus services" class="service-navigation">
      <RouterLink to="/home" class="nav-link home-link" active-class="is-active">
        <House aria-hidden="true" /><span>Home</span>
      </RouterLink>
      <div v-for="group in moduleGroups" :key="group" class="nav-group">
        <p class="group-label">{{ group }}</p>
        <RouterLink v-for="module in campusModules.filter(item => item.group === group)" :key="module.id"
                    :to="module.path" class="nav-link" active-class="is-active">
          <component :is="module.icon" aria-hidden="true" /><span>{{ module.name }}</span>
        </RouterLink>
      </div>
    </nav>
    <div class="sidebar-footer"><span class="footer-dot" aria-hidden="true"></span> Campus service directory</div>
  </div>
</template>

<script setup>
import { RouterLink } from 'vue-router'
import { House, School } from '@element-plus/icons-vue'
import { campusModules, moduleGroups } from '../config/modules.js'
</script>

<style scoped>
.campus-sidebar { display: flex; flex-direction: column; height: 100%; padding: 28px 14px 20px; }
.campus-brand { display: flex; align-items: center; gap: 12px; padding: 0 10px 30px; text-decoration: none; color: #183858; }
.brand-icon { display: grid; place-items: center; flex-shrink: 0; width: 40px; height: 40px; border-radius: 12px; background: #2467aa; color: white; }
.brand-icon svg { width: 24px; height: 24px; }
.campus-brand strong { display: block; font-size: 19px; letter-spacing: -.6px; }
.campus-brand small { display: block; margin-top: 3px; color: #7c8b9e; font-size: 10px; letter-spacing: .3px; }
.service-navigation { flex: 1; }
.nav-group { margin-top: 24px; }
.group-label { padding: 0 15px; margin-bottom: 10px; color: #718096; font-size: 10px; font-weight: 700; text-transform: uppercase; letter-spacing: 1.3px; }
.nav-link { display: flex; align-items: center; gap: 10px; margin: 4px 0; padding: 13px 12px; border-radius: 8px; color: #53677f; text-decoration: none; font-size: 12px; line-height: 1.5; }
.nav-link svg { flex-shrink: 0; width: 18px; height: 18px; }
.nav-link:hover { background: #f3f7fc; color: #245e9d; }
.nav-link.is-active { color: #245e9d; background: #eaf2fc; font-weight: 700; }
.nav-link:focus-visible, .campus-brand:focus-visible { outline: 3px solid #346bb9; outline-offset: 2px; }
.sidebar-footer { display: flex; align-items: center; gap: 7px; margin: 28px 10px 0; padding-top: 22px; border-top: 1px solid #e7edf4; color: #7b8b9c; font-size: 10px; }
.footer-dot { width: 6px; height: 6px; border-radius: 50%; background: #4b9587; }
@media (max-width: 900px) {
  .campus-sidebar { padding: 4px 12px; }
  .campus-brand, .group-label, .sidebar-footer { display: none; }
  .service-navigation, .nav-group { display: flex; align-items: center; gap: 7px; }
  .service-navigation { overflow-x: auto; overflow-y: hidden; scrollbar-width: thin; padding: 2px; }
  .nav-group { margin: 0; }
  .nav-link { margin: 0; padding: 10px 12px; white-space: nowrap; }
}
</style>
