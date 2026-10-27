<template>
  <section class="admin-page">
    <RouterLink to="/home" class="back-link">Back to home</RouterLink>
    <header class="page-heading"><div><p class="eyebrow">Administration</p><h1>Progress Notifications</h1><p>Read application updates and review the processing history.</p></div></header>
    <div v-if="error" role="alert" class="error-message">{{ error }} <RouterLink v-if="expired" to="/Login">Sign in</RouterLink><button v-else class="text-link" @click="initialize">Retry</button></div>
    <p v-if="loading" role="status">Loading notifications…</p>
    <template v-else-if="user">
      <div class="notice-toolbar"><p><strong>{{ unread }}</strong> unread notifications</p><label class="check-label"><input v-model="onlyUnread" type="checkbox" @change="page = 1; loadNotices()">Unread only</label><button :disabled="busy || !unread" @click="readAll">Mark all as read</button><button :disabled="busy" @click="loadNotices">Refresh</button></div>
      <p v-if="user.role === 'STAFF'" class="muted">These notifications belong to your own account. Open Document Applications to review student requests.</p>
      <p v-if="busy" role="status">Updating notifications…</p>
      <ol class="notice-list"><li v-for="notice in notices" :key="notice.id" :class="{ unread: !notice.isRead }"><div><span v-if="!notice.isRead" class="unread-label">Unread</span><h2>{{ notice.title }}</h2><p class="preserve">{{ notice.content }}</p><time>{{ dateLabel(notice.createdAt) }}</time></div><div class="notice-actions"><button class="text-link" :disabled="busy" @click="openNotice(notice)">View application</button><button v-if="!notice.isRead" :disabled="busy" @click="readOne(notice)">Mark as read</button></div></li></ol>
      <p v-if="!notices.length" class="empty">{{ onlyUnread ? 'You have no unread notifications.' : 'No notifications yet. Application updates will appear here.' }}</p>
      <nav class="pagination" aria-label="Notification pages"><button :disabled="page <= 1 || busy" @click="page--; loadNotices()">Previous</button><span>Page {{ page }} · {{ total }} notifications</span><button :disabled="page * 10 >= total || busy" @click="page++; loadNotices()">Next</button></nav>
    </template>
    <el-dialog v-model="detailOpen" title="Application progress" width="min(820px, 94vw)"><p v-if="detailBusy" role="status">Loading application…</p><p v-if="detailError" role="alert" class="error-message">{{ detailError }}</p><ApplicationDetail v-if="detail && !detailBusy" :detail="detail"><div class="form-actions"><RouterLink :to="{ path: '/applications', query: { id: detail.application.id } }">Open in Document Applications</RouterLink></div></ApplicationDetail></el-dialog>
  </section>
</template>
<script setup>
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import ApplicationDetail from '../../components/administration/ApplicationDetail.vue'
import * as api from '../../api/administration.js'
import '../../styles/administration.css'
defineProps({ module: { type: Object, default: null } })
const { dateLabel } = api
const user = ref(null), loading = ref(true), busy = ref(false), error = ref(''), expired = ref(false)
const notices = ref([]), unread = ref(0), total = ref(0), page = ref(1), onlyUnread = ref(false)
const detailOpen = ref(false), detailBusy = ref(false), detailError = ref(''), detail = ref(null)
let sequence = 0
async function loadNotices() {
  const current = ++sequence; busy.value = true; error.value = ''; expired.value = false
  try {
    const [list, count] = await Promise.all([api.listNotifications({ unread: onlyUnread.value || undefined, pageNum: page.value }), api.unreadCount()])
    if (sequence !== current) return
    notices.value = list.records; total.value = list.total; unread.value = count.count
    if (!list.records.length && page.value > 1) { page.value--; await loadNotices() }
  } catch (e) { if (sequence === current) { error.value = e.message; expired.value = e.status === 401 } }
  finally { if (sequence === current) busy.value = false }
}
async function initialize() {
  loading.value = true; error.value = ''; expired.value = false
  try { user.value = await api.loadAdministrationSession(); await loadNotices() } catch (e) { error.value = e.message; expired.value = e.status === 401 }
  finally { loading.value = false }
}
async function readOne(notice) {
  busy.value = true
  try { await api.markNotificationRead(notice.id); await loadNotices() } catch (e) { error.value = e.message } finally { busy.value = false }
}
async function readAll() {
  busy.value = true
  try { await api.markAllNotificationsRead(); await loadNotices() } catch (e) { error.value = e.message } finally { busy.value = false }
}
async function openNotice(notice) {
  detailOpen.value = true; detailBusy.value = true; detailError.value = ''; detail.value = null
  try { detail.value = await api.applicationDetail(notice.applicationId); await api.markNotificationRead(notice.id); await loadNotices() }
  catch (e) { detailError.value = e.message }
  finally { detailBusy.value = false }
}
onMounted(initialize)
</script>
