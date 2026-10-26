<template>
  <header class="campus-header">
    <span class="header-title">SmartCampus</span>
    <div class="account-actions">
      <span class="account-label">{{ accountName }}<span v-if="roleLabel" class="role-label"> / {{ roleLabel }}</span></span>
      <button class="signout-button" :disabled="signingOut" @click="handleSignOut">{{ signingOut ? 'Signing out…' : 'Sign Out' }}</button>
    </div>
  </header>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '../utils/request.js'
import { signOut } from '../utils/session.js'

const router = useRouter()
const route = useRoute()
const signingOut = ref(false)
const account = computed(() => {
  // The guard refreshes this server-backed snapshot before protected navigation.
  void route.fullPath
  try {
    return { user: JSON.parse(globalThis.sessionStorage?.getItem('user') || '{}'), role: JSON.parse(globalThis.sessionStorage?.getItem('identity') || 'null') }
  } catch { return { user: {}, role: null } }
})
const accountName = computed(() => account.value.user?.name || account.value.user?.username || 'Account')
const roleLabel = computed(() => ({ stu: 'Student', dormManager: 'Staff', admin: 'Administrator' })[account.value.role] || '')

async function handleSignOut() {
  if (signingOut.value) return
  signingOut.value = true
  try {
    await signOut(request, window.sessionStorage)
    await router.replace({ name: 'Login' })
    ElMessage.success('Signed out successfully.')
  } catch {
    ElMessage.error('Unable to sign out. Please try again.')
  } finally { signingOut.value = false }
}
</script>

<style scoped>
.campus-header { display: flex; align-items: center; justify-content: space-between; gap: 16px; min-height: 68px; padding: 12px 32px; border-bottom: 1px solid #e0e7ef; background: #fff; }
.header-title { color: #61738a; font-size: 13px; font-weight: 600; }
.account-actions { display: flex; align-items: center; justify-content: flex-end; gap: 18px; min-width: 0; }
.account-label { color: #405773; font-size: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.role-label { color: #79889b; }
.signout-button { flex-shrink: 0; padding: 8px 13px; border: 1px solid #d9e3ef; border-radius: 7px; background: white; color: #405773; font: inherit; font-size: 12px; cursor: pointer; }
.signout-button:hover { background: #f2f6fc; }
.signout-button:disabled { opacity: .6; cursor: wait; }
.signout-button:focus-visible { outline: 3px solid #346bb9; outline-offset: 3px; }
@media (max-width: 600px) { .campus-header { padding: 12px 16px; min-height: 58px; } .header-title, .role-label { display: none; } .account-actions { width: 100%; justify-content: space-between; } }
</style>
