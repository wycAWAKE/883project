<template>
  <section aria-labelledby="repair-my-reports">
    <div class="repair-section-heading"><div><h2 id="repair-my-reports">My fault reports</h2><p class="repair-muted">Track your reports from submission to completion.</p></div>
      <button class="repair-button repair-primary" type="button" @click="state.createOpen = true; state.submitError = ''">New report</button></div>
    <p v-if="state.submitSuccess" class="repair-alert repair-alert-success" role="status">{{ state.submitSuccess }}</p>
    <form class="repair-filters" @submit.prevent="model.applyFilters">
      <div><label for="repair-search">Search reports</label><input id="repair-search" v-model="state.keyword" type="search" maxlength="100" placeholder="Search by title" /></div>
      <div><label for="repair-status">Status</label><select id="repair-status" v-model="state.status" @change="model.applyFilters"><option value="">All statuses</option><option v-for="item in repairStatuses" :key="item.value" :value="item.value">{{ item.label }}</option></select></div>
      <button class="repair-button" type="submit">Search</button>
    </form>
    <div class="repair-list-panel" :aria-busy="state.listLoading">
      <p v-if="state.listLoading" class="repair-loading" role="status">Loading your reports…</p>
      <div v-else-if="state.listError" class="repair-empty" role="alert"><h3>Unable to load reports</h3><p>{{ state.listError }}</p><button class="repair-button" type="button" @click="model.loadList">Retry reports</button></div>
      <div v-else-if="!state.records.length" class="repair-empty"><h3>{{ state.keyword || state.status ? 'No matching reports' : 'No reports yet' }}</h3><p>{{ state.keyword || state.status ? 'Try another title or status.' : 'Report a facilities issue to start tracking its progress.' }}</p></div>
      <ul v-else class="repair-report-list">
        <li v-for="ticket in state.records" :key="ticket.id"><div class="repair-report-main"><span class="repair-report-id">M4-{{ ticket.id }}</span><button class="repair-title-link" type="button" @click="$emit('select-ticket', String(ticket.id))">{{ ticket.title }}</button><p class="repair-muted">{{ locationLabel(ticket.locationType) }} · {{ ticket.locationText }}</p></div>
          <span class="repair-badge" :data-status="ticket.status">{{ statusLabel(ticket.status) }}</span><time class="repair-muted">{{ ticket.createdAt }}</time>
          <button class="repair-button" type="button" :aria-label="`View report M4-${ticket.id}`" @click="$emit('select-ticket', String(ticket.id))">View</button></li>
      </ul>
    </div>
    <nav class="repair-pagination" aria-label="Report pagination"><p>{{ state.total }} {{ state.total === 1 ? 'report' : 'reports' }} · Page {{ state.pageNum }} of {{ Math.max(1, Math.ceil(state.total / state.pageSize)) }}</p>
      <div><label for="repair-page-size">Per page</label><select id="repair-page-size" :value="state.pageSize" @change="model.changeSize($event.target.value)"><option :value="10">10</option><option :value="20">20</option><option :value="50">50</option></select>
        <button class="repair-button" type="button" :disabled="state.listLoading || state.pageNum <= 1" @click="model.changePage(state.pageNum - 1)">Previous</button><button class="repair-button" type="button" :disabled="state.listLoading || state.pageNum * state.pageSize >= state.total" @click="model.changePage(state.pageNum + 1)">Next</button></div>
    </nav>
    <RepairCreateDialog :open="state.createOpen" :form="state.form" :errors="state.formErrors" :submitting="state.submitting" :error="state.submitError" :uncertain="state.uncertain" @close="state.createOpen = false" @submit="submit" @check-reports="checkReports" />
    <RepairDetailDrawer :open="state.detailOpen" :loading="state.detailLoading" :error="state.detailError" :ticket="state.detail" :history="state.history" @close="closeDetails" @refresh="model.openTicket(ticketId)" />
  </section>
</template>
<script setup>
import { inject, onMounted, onBeforeUnmount, watch } from 'vue'
import { REPAIR_API_KEY, createRepairsApi } from '../../api/repairs.js'
import { createRepairStudentModel, repairStatuses, locationLabel, statusLabel, repairDraftStore } from '../../utils/repair.js'
import RepairCreateDialog from './RepairCreateDialog.vue'
import RepairDetailDrawer from './RepairDetailDrawer.vue'
const props = defineProps({ username: { type: String, required: true }, ticketId: [String, Array] })
const emit = defineEmits(['select-ticket', 'close-ticket', 'expired'])
const api = inject(REPAIR_API_KEY, null) || createRepairsApi()
const drafts = repairDraftStore(typeof window === 'undefined' ? null : window.sessionStorage, props.username)
const model = createRepairStudentModel({ api, drafts, onExpired: () => emit('expired') })
const state = model.state
let mounted = false
onMounted(() => { mounted = true; model.loadList(); if (props.ticketId != null) model.openTicket(props.ticketId) })
watch(() => props.ticketId, id => { if (mounted) { if (id == null) model.closeDetail(); else model.openTicket(id) } })
onBeforeUnmount(model.dispose)
function closeDetails() { model.closeDetail(); emit('close-ticket') }
async function submit() { const ticket = await model.submit(); if (ticket) emit('select-ticket', String(ticket.id)) }
function checkReports() { state.createOpen = false; model.loadList() }
</script>
