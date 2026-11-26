<template>
  <section aria-labelledby="repair-maintenance-queue">
    <div class="repair-section-heading"><div><h2 id="repair-maintenance-queue">Maintenance queue</h2><p class="repair-muted">Review reports, assign staff and track maintenance work.</p></div><span class="repair-badge">{{ actor.identity === 'admin' ? 'Administrator' : 'Staff' }}</span></div>
    <p v-if="state.notice && !state.detailOpen" class="repair-alert repair-alert-success" role="status">{{ state.notice }}</p>
    <form class="repair-filters" @submit.prevent="model.applyFilters">
      <div><label for="repair-staff-search">Search reports</label><input id="repair-staff-search" v-model="state.keyword" type="search" maxlength="100" placeholder="Search by title" /></div>
      <div><label for="repair-staff-status">Status</label><select id="repair-staff-status" v-model="state.status" @change="model.applyFilters"><option value="">All statuses</option><option v-for="item in repairStatuses" :key="item.value" :value="item.value">{{ item.label }}</option></select></div>
      <button class="repair-button" type="submit">Search</button>
    </form>
    <label v-if="actor.identity === 'dormManager'" class="repair-mine-filter"><input v-model="state.mine" type="checkbox" @change="model.applyFilters" /> Only assigned to me</label>
    <div class="repair-list-panel" :aria-busy="state.listLoading">
      <p v-if="state.listLoading" class="repair-loading" role="status">Loading maintenance queue…</p>
      <div v-else-if="state.listError" class="repair-empty" role="alert"><h3>Unable to load reports</h3><p>{{ state.listError }}</p><button class="repair-button" @click="model.loadList">Retry reports</button></div>
      <div v-else-if="!state.records.length" class="repair-empty"><h3>No matching reports</h3><p>Try another filter or check again after a student submits a report.</p></div>
      <ul v-else class="repair-report-list">
        <li v-for="ticket in state.records" :key="ticket.id"><div class="repair-report-main"><span class="repair-report-id">M4-{{ ticket.id }}</span><button class="repair-title-link" @click="$emit('select-ticket',String(ticket.id))">{{ ticket.title }}</button><p class="repair-muted">{{ ticket.locationText }} · {{ ticket.assigneeUsername || 'Unassigned' }}</p></div>
          <span class="repair-badge" :data-status="ticket.status">{{ statusLabel(ticket.status) }}</span><time class="repair-muted">{{ ticket.updatedAt }}</time><button class="repair-button" :aria-label="`View report M4-${ticket.id}`" @click="$emit('select-ticket',String(ticket.id))">View</button></li>
      </ul>
    </div>
    <nav class="repair-pagination" aria-label="Report pagination"><p>{{ state.total }} {{ state.total === 1 ? 'report' : 'reports' }} · Page {{ state.pageNum }} of {{ Math.max(1,Math.ceil(state.total/state.pageSize)) }}</p><div><label for="repair-staff-size">Per page</label><select id="repair-staff-size" :value="state.pageSize" @change="model.changeSize($event.target.value)"><option :value="10">10</option><option :value="20">20</option><option :value="50">50</option></select><button class="repair-button" :disabled="state.listLoading || state.pageNum <= 1" @click="model.changePage(state.pageNum-1)">Previous</button><button class="repair-button" :disabled="state.listLoading || state.pageNum*state.pageSize >= state.total" @click="model.changePage(state.pageNum+1)">Next</button></div></nav>
    <RepairDetailDrawer :open="state.detailOpen" :loading="state.detailLoading" :error="state.detailError" :ticket="state.detail" :history="state.history" :busy="state.busy" :notice="state.notice" @close="closeDetails" @refresh="model.refreshTicket">
      <template #actions>
        <section class="repair-staff-actions" aria-labelledby="repair-actions-heading">
          <h3 id="repair-actions-heading">Maintenance actions</h3>
          <div v-if="available.length" class="repair-action-toolbar"><button v-for="kind in available" :key="kind" type="button" class="repair-button" :disabled="state.busy" @click="model.beginOperation(kind)">{{ staffActionLabels[kind] }}</button></div>
          <p v-if="!available.length || (actor.identity === 'dormManager' && state.detail?.assigneeUsername && state.detail.assigneeUsername !== actor.user.username)" class="repair-muted">{{ state.detail?.status === 'CLOSED' ? 'This report is closed. No further actions are available.' : 'Only the assigned staff member or an administrator can process this report.' }}</p>
          <form v-if="state.operation" class="repair-form repair-action-form" @submit.prevent="perform">
            <h4>{{ staffActionLabels[state.operation] }}</h4>
            <fieldset :disabled="state.busy || state.detailLoading">
              <template v-if="state.operation === 'assign'"><label for="repair-assignee">Assign to</label><select id="repair-assignee" v-model="state.assigneeUsername" :disabled="state.staffLoading || state.busy"><option value="">Choose a staff member</option><option v-for="item in state.staff" :key="item.username" :value="item.username">{{ item.displayName }} ({{ item.username }})</option></select><p v-if="state.staffLoading" class="repair-muted" role="status">Loading staff…</p><div v-if="state.staffError" class="repair-field-error" role="alert">{{ state.staffError }} <button class="repair-button" type="button" @click="model.loadStaff">Retry staff</button></div></template>
              <label for="repair-action-note">Processing note{{ ['assign','start'].includes(state.operation) ? ' (optional)' : '' }}</label><textarea id="repair-action-note" v-model="state.note" rows="3" :maxlength="state.operation === 'assign' ? 1800 : 2000" :required="!['assign','start'].includes(state.operation)" />
            </fieldset>
            <p v-if="state.operationError" class="repair-alert repair-alert-error" role="alert">{{ state.operationError }}</p>
            <div v-if="state.requiresReview" class="repair-conflict" role="status"><p>The report may have changed. Review its current status and history before confirming again.</p><button type="button" class="repair-button" :disabled="state.detailLoading || state.busy" @click="model.refreshTicket">Refresh report</button><button type="button" class="repair-button" :disabled="state.detailLoading || state.busy || !state.reviewReady || !available.includes(state.operation)" @click="model.acceptRefreshed">Use refreshed report</button></div>
            <footer class="repair-dialog-actions"><button type="button" class="repair-button" :disabled="state.busy" @click="model.cancelOperation">Cancel action</button><button type="submit" class="repair-button repair-primary" :disabled="state.busy || state.requiresReview || state.staffLoading || !available.includes(state.operation)">{{ state.busy ? 'Saving…' : staffConfirmLabels[state.operation] }}</button></footer>
          </form>
        </section>
      </template>
    </RepairDetailDrawer>
  </section>
</template>
<script setup>
import { computed, inject, onMounted, onBeforeUnmount, watch } from 'vue'
import { REPAIR_API_KEY, createRepairsApi } from '../../api/repairs.js'
import { createRepairStaffModel, repairStatuses, statusLabel, staffActionLabels, staffConfirmLabels, repairStaffDraftStore } from '../../utils/repair.js'
import RepairDetailDrawer from './RepairDetailDrawer.vue'
const props = defineProps({ actor: { type: Object, required: true }, ticketId: [String,Array] })
const emit = defineEmits(['select-ticket','close-ticket','expired'])
const api = inject(REPAIR_API_KEY,null) || createRepairsApi()
const drafts = repairStaffDraftStore(typeof window === 'undefined' ? null : window.sessionStorage,props.actor)
const model = createRepairStaffModel({ api, actor:props.actor, drafts, onExpired:() => emit('expired') })
const state = model.state, available = computed(() => model.actions())
let mounted = false
onMounted(() => { mounted = true; model.loadList(); if (props.ticketId != null) model.openTicket(props.ticketId) })
watch(() => props.ticketId,id => { if (mounted) { if (id == null) model.closeDetail(); else model.openTicket(id) } })
onBeforeUnmount(model.dispose)
function closeDetails() { if (!state.busy) { model.closeDetail(); emit('close-ticket') } }
async function perform() { await model.perform(); if (!state.detailOpen) emit('close-ticket') }
</script>
