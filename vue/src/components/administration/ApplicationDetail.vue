<template>
  <div v-if="detail" class="application-detail">
    <header class="detail-heading"><div><p class="eyebrow">{{ detail.application.referenceNo }}</p><h2>{{ typeLabel(detail.application.applicationType) }}</h2></div><span :class="['state', detail.application.status]">{{ statusLabel(detail.application.status) }}</span></header>
    <dl class="facts">
      <div><dt>Applicant</dt><dd>{{ detail.application.applicantName }} ({{ detail.application.studentNumber }})</dd></div>
      <div><dt>Email</dt><dd>{{ detail.application.email || 'Not provided' }}</dd></div>
      <div><dt>Document language</dt><dd>{{ detail.application.documentLanguage === 'ENGLISH' ? 'English' : 'Chinese' }}</dd></div>
      <div><dt>Delivery</dt><dd>{{ detail.application.deliveryMethod === 'PICKUP' ? 'Collection in person' : 'Electronic copy' }}</dd></div>
    </dl>
    <h3>Purpose</h3><p class="preserve">{{ detail.application.purpose }}</p>
    <template v-if="detail.application.notes"><h3>Additional information</h3><p class="preserve">{{ detail.application.notes }}</p></template>
    <aside v-if="detail.application.latestReason" class="review-reason"><h3>Latest review reason</h3><p class="preserve">{{ detail.application.latestReason }}</p></aside>
    <h3>Current supporting files</h3><p v-if="!activeFiles.length" class="muted">No supporting files attached.</p>
    <ul v-else class="files"><li v-for="file in activeFiles" :key="file.id"><button type="button" class="text-link" @click="download(file)">{{ file.filename }}</button><small>{{ Math.ceil(file.fileSize / 1024) }} KB</small></li></ul>
    <h3>Processing history</h3>
    <ol class="history"><li v-for="event in detail.history" :key="event.id"><div><strong>{{ actionLabel(event.action) }}</strong><span class="muted">Revision {{ event.revision }}</span></div><p>{{ event.actorName }} · {{ dateLabel(event.createdAt) }}</p><p v-if="event.reason" class="preserve">{{ event.reason }}</p>
      <details><summary>View submitted information</summary><p><strong>Purpose:</strong> {{ readSnapshot(event).purpose }}</p><p><strong>Notes:</strong> {{ readSnapshot(event).notes || 'None' }}</p><ul class="files"><li v-for="file in readSnapshot(event).attachments || []" :key="file.id"><button type="button" class="text-link" @click="download(file)">{{ file.filename }}</button></li></ul></details>
    </li></ol>
    <p v-if="error" class="error-message" role="alert">{{ error }}</p>
    <slot />
  </div>
</template>
<script setup>
import { computed, ref } from 'vue'
import { downloadFile, statusLabel, typeLabel, actionLabel, dateLabel, readSnapshot } from '../../api/administration.js'
const props = defineProps({ detail: { type: Object, default: null } })
const activeFiles = computed(() => props.detail?.attachments.filter(file => file.active) || [])
const error = ref('')
async function download(file) { error.value = ''; try { await downloadFile(file) } catch (e) { error.value = e.message } }
</script>
