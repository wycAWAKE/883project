<template>
  <dialog ref="dialog" class="repair-dialog repair-detail" aria-labelledby="repair-detail-title" @cancel.prevent="close">
    <header class="repair-dialog-header"><div><p class="repair-eyebrow">Maintenance progress</p><h2 id="repair-detail-title">Report details</h2></div>
      <button class="repair-button" type="button" aria-label="Close report details" :disabled="busy" @click="close">Close</button></header>
    <p v-if="notice" class="repair-alert repair-alert-success" role="status">{{ notice }}</p>
    <p v-if="loading" class="repair-loading" role="status">Loading report details…</p>
    <div v-else-if="error" class="repair-alert repair-alert-error" role="alert"><p>{{ error }}</p><button class="repair-button" type="button" :disabled="busy" @click="$emit('refresh')">Retry details</button></div>
    <template v-else-if="ticket">
      <div class="repair-detail-heading"><span class="repair-badge" :data-status="ticket.status">{{ statusLabel(ticket.status) }}</span><span class="repair-muted">M4-{{ ticket.id }}</span></div>
      <h3 class="repair-ticket-title">{{ ticket.title }}</h3>
      <p class="repair-description">{{ ticket.description }}</p>
      <dl class="repair-facts">
        <div><dt>Location</dt><dd>{{ locationLabel(ticket.locationType) }} · {{ ticket.locationText }}</dd></div>
        <div><dt>Assigned to</dt><dd>{{ ticket.assigneeUsername || 'Not assigned yet' }}</dd></div>
        <div><dt>Submitted</dt><dd>{{ ticket.createdAt }}</dd></div>
        <div v-if="ticket.resolvedAt"><dt>Resolved</dt><dd>{{ ticket.resolvedAt }}</dd></div>
        <div v-if="ticket.closedAt"><dt>Closed</dt><dd>{{ ticket.closedAt }}</dd></div>
      </dl>
      <slot name="actions" :ticket="ticket" />
      <div class="repair-section-heading"><h3>Processing history</h3><button class="repair-button" type="button" :disabled="busy" @click="$emit('refresh')">Refresh progress</button></div>
      <ol v-if="history.length" class="repair-timeline">
        <li v-for="item in history" :key="item.id"><div class="repair-timeline-title"><strong>{{ actionLabel(item.action) }}</strong><time>{{ item.createdAt }}</time></div>
          <p class="repair-muted">{{ item.actorUsername }} · {{ statusLabel(item.toStatus) }}</p><p class="repair-description">{{ item.note }}</p></li>
      </ol>
      <p v-else class="repair-muted">No processing history is available yet.</p>
      <p class="repair-timezone">Times shown in Asia/Shanghai.</p>
    </template>
  </dialog>
</template>
<script setup>
import { ref, watch, onBeforeUnmount } from 'vue'
import { statusLabel, locationLabel, actionLabel } from '../../utils/repair.js'
const props = defineProps({ open: Boolean, loading: Boolean, busy: Boolean, notice: String, error: String, ticket: Object, history: { type: Array, default: () => [] } })
const emit = defineEmits(['close', 'refresh'])
function close() { if (!props.busy) emit('close') }
const dialog = ref(null)
watch(() => props.open, open => {
  if (open && dialog.value && !dialog.value.open) dialog.value.showModal()
  else if (!open && dialog.value?.open) dialog.value.close()
}, { flush: 'post' })
watch(dialog, element => { if (element && props.open && !element.open) element.showModal() }, { flush: 'post' })
onBeforeUnmount(() => { if (dialog.value?.open) dialog.value.close() })
</script>
