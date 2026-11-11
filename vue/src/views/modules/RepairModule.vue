<template>
  <section class="repair-page" aria-labelledby="M4-title">
    <RouterLink to="/home" class="repair-back">← Back to home</RouterLink>
    <header class="repair-page-heading"><span class="repair-page-icon" aria-hidden="true"><component :is="module.icon" /></span><div><p class="repair-eyebrow">{{ module.group }} / {{ module.id }}</p><h1 id="M4-title">{{ module.name }}</h1></div></header>
    <p class="repair-page-description">Report a facilities issue and follow its progress through to resolution.</p>
    <p v-if="loading" class="repair-loading" role="status">Verifying your session…</p>
    <div v-else-if="sessionError" class="repair-alert repair-alert-error" role="alert"><p>{{ sessionError }}</p><button class="repair-button" type="button" @click="loadSession">Retry session</button></div>
    <RepairStudentPanel v-else-if="session?.identity === 'stu'" :key="session.user.username" :username="session.user.username" :ticket-id="route.query.ticketId" @select-ticket="selectTicket" @close-ticket="closeTicket" @expired="expired" />
    <RepairStaffPanel v-else-if="session" :key="`${session.identity}:${session.user.username}`" :actor="session" :ticket-id="route.query.ticketId" @select-ticket="selectTicket" @close-ticket="closeTicket" @expired="expired" />
  </section>
</template>
<script setup>
import { inject, provide, ref, onMounted, onBeforeUnmount } from 'vue'
import { RouterLink, useRouter, useRoute } from 'vue-router'
import { REPAIR_API_KEY, REPAIR_SESSION_KEY, createRepairsApi } from '../../api/repairs.js'
import { clearSession, saveSession } from '../../utils/session.js'
import RepairStudentPanel from '../../components/repair/RepairStudentPanel.vue'
import RepairStaffPanel from '../../components/repair/RepairStaffPanel.vue'
defineProps({ module: { type: Object, required: true } })
const api = inject(REPAIR_API_KEY, null) || createRepairsApi()
provide(REPAIR_API_KEY, api)
const supplied = inject(REPAIR_SESSION_KEY, null)
const session = ref(supplied), loading = ref(!supplied), sessionError = ref('')
const router = useRouter(), route = useRoute()
let disposed = false, redirecting = false
async function expired() {
  if (redirecting || disposed) return
  redirecting = true
  if (typeof window !== 'undefined') clearSession(window.sessionStorage)
  await router.replace({ name: 'Login', query: { reason: 'expired', redirect: route.fullPath } })
}
async function loadSession() {
  loading.value = true; sessionError.value = ''
  try {
    const value = await api.session()
    if (disposed) return
    session.value = value
    if (typeof window !== 'undefined') saveSession(window.sessionStorage, value.user, value.identity)
  } catch (error) {
    if (!disposed) { if (error.status === 401) await expired(); else sessionError.value = error.message || 'Unable to verify your session.' }
  } finally { if (!disposed) loading.value = false }
}
function selectTicket(id) { router.push({ path: '/repairs', query: { ...route.query, ticketId: id } }) }
function closeTicket() { const query = { ...route.query }; delete query.ticketId; router.replace({ path: '/repairs', query }) }
onMounted(() => { if (!supplied) loadSession() })
onBeforeUnmount(() => { disposed = true })
</script>
<style src="../../assets/css/repair.css"></style>
