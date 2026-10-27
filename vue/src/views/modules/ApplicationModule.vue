<template>
  <section class="admin-page">
    <RouterLink to="/home" class="back-link">Back to home</RouterLink>
    <header class="page-heading"><div><p class="eyebrow">Administration</p><h1>Document Applications</h1><p>{{ isStaff ? 'Review student applications and record your decision.' : 'Submit a document request and follow its progress.' }}</p></div><button v-if="user?.role === 'STUDENT'" class="primary" @click="newApplication">New application</button></header>
    <div v-if="error" class="error-message" role="alert">{{ error }} <RouterLink v-if="expired" to="/Login">Sign in</RouterLink><button v-else type="button" class="text-link" @click="initialize">Retry</button></div>
    <p v-if="success" class="success-message" role="status">{{ success }}</p>
    <p v-if="loading" role="status">Loading applications…</p>
    <template v-else-if="user">
      <dl class="summary-row"><div v-for="state in states" :key="state"><dt>{{ statusLabel(state) }}</dt><dd>{{ summary[state] || 0 }}</dd></div></dl>
      <form class="filters" @submit.prevent="page = 1; loadList()"><label>Status<select v-model="status"><option value="">All statuses</option><option v-for="state in states" :key="state" :value="state">{{ statusLabel(state) }}</option></select></label><label class="search">Search<input v-model="search" maxlength="100" placeholder="Reference, name or student account"></label><button :disabled="listBusy">Search</button></form>
      <p v-if="listBusy" role="status">Updating list…</p>
      <div class="table-wrap"><table><thead><tr><th>Reference</th><th v-if="isStaff">Applicant</th><th>Document</th><th>Status</th><th>Updated</th><th>Details</th></tr></thead><tbody><tr v-for="app in applications" :key="app.id"><td>{{ app.referenceNo }}</td><td v-if="isStaff">{{ app.applicantName }}<small>{{ app.studentNumber }}</small></td><td>{{ typeLabel(app.applicationType) }}</td><td><span :class="['state', app.status]">{{ statusLabel(app.status) }}</span></td><td>{{ dateLabel(app.updatedAt) }}</td><td><button class="text-link" @click="showDetail(app.id)">View</button></td></tr><tr v-if="!applications.length"><td :colspan="isStaff ? 6 : 5" class="empty">No applications match this view.</td></tr></tbody></table></div>
      <nav class="pagination" aria-label="Application pages"><button :disabled="page <= 1 || listBusy" @click="page--; loadList()">Previous</button><span>Page {{ page }} · {{ total }} applications</span><button :disabled="page * 10 >= total || listBusy" @click="page++; loadList()">Next</button></nav>
    </template>
    <el-dialog v-model="formOpen" :title="editingId ? 'Revise returned application' : 'New document application'" width="min(720px, 94vw)" :close-on-click-modal="false" :before-close="closeForm">
      <form id="application-form" class="application-form" @submit.prevent="save">
        <p class="muted">Applicant: {{ user?.fullName }} ({{ user?.studentNumber }}). Your identity comes from your signed-in account.</p>
        <label>Document type<select v-model="form.applicationType" :disabled="saving"><option value="ENROLMENT_CERTIFICATE">Enrolment certificate</option><option value="TRANSCRIPT">Transcript</option></select></label>
        <label>Purpose <span class="muted">Required, 5–1,000 characters</span><textarea v-model="form.purpose" minlength="5" maxlength="1000" required rows="3" :disabled="saving" /></label>
        <div class="form-columns"><label>Document language<select v-model="form.documentLanguage" :disabled="saving"><option value="ENGLISH">English</option><option value="CHINESE">Chinese</option></select></label><label>Delivery method<select v-model="form.deliveryMethod" :disabled="saving"><option value="ELECTRONIC">Electronic copy</option><option value="PICKUP">Collection in person</option></select></label></div>
        <label>Additional information<textarea v-model="form.notes" maxlength="1000" rows="2" :disabled="saving" /></label>
        <fieldset :disabled="saving || uploading"><legend>Supporting files</legend><p class="muted">PDF, PNG or JPG. Up to 5 files, 5 MB each. A transcript request needs at least one file.</p><label class="upload-label">Choose file<input ref="fileInput" type="file" accept=".pdf,.png,.jpg,.jpeg" @change="upload"></label><p v-if="uploading" role="status">Uploading file…</p>
          <ul class="files"><li v-for="file in selectedFiles" :key="file.id"><span>{{ file.filename }}</span><button type="button" class="text-link" @click="removeSelected(file)">Remove</button></li></ul>
          <details v-if="unusedFiles.length"><summary>Previously uploaded files ({{ unusedFiles.length }})</summary><ul class="files"><li v-for="file in unusedFiles" :key="file.id"><span>{{ file.filename }}</span><button type="button" class="text-link" :disabled="selectedFiles.length >= 5" @click="selectedFiles.push(file)">Attach</button><button type="button" class="text-link danger" @click="deleteUnused(file)">Delete</button></li></ul></details>
        </fieldset>
        <p v-if="formError" class="error-message" role="alert">{{ formError }}</p>
        <div class="form-actions"><button type="button" :disabled="saving || uploading" @click="formOpen = false">Cancel</button><button class="primary" :disabled="saving || uploading">{{ saving ? 'Submitting…' : editingId ? 'Resubmit application' : 'Submit application' }}</button></div>
      </form>
    </el-dialog>
    <el-dialog v-model="detailOpen" title="Application details" width="min(820px, 94vw)">
      <p v-if="detailBusy" role="status">Loading application…</p><p v-if="detailError" role="alert" class="error-message">{{ detailError }}</p>
      <ApplicationDetail v-if="detail && !detailBusy" :detail="detail">
        <div v-if="!isStaff && detail.application.status === 'RETURNED'" class="form-actions"><button class="primary" @click="revise">Revise and resubmit</button></div>
        <form v-if="isStaff && detail.application.status === 'PENDING'" class="review-form" @submit.prevent="prepareReview"><h3>Review decision</h3><label>Decision<select v-model="decision.action" :disabled="saving"><option value="RETURN">Return for more information</option><option value="APPROVE">Approve</option><option value="REJECT">Reject</option></select></label><label>Reason {{ decision.action === 'APPROVE' ? '(optional)' : '(required)' }}<textarea v-model="decision.reason" :required="decision.action !== 'APPROVE'" maxlength="1000" rows="3" :disabled="saving" /></label><p v-if="reviewError" class="error-message" role="alert">{{ reviewError }}</p><button v-if="!reviewReady" class="primary" :disabled="saving">Review decision</button><div v-else class="review-confirmation"><p>Save <strong>{{ decision.action === 'RETURN' ? 'Return' : decision.action === 'APPROVE' ? 'Approve' : 'Reject' }}</strong> for {{ detail.application.referenceNo }}? The student will receive a notification.</p><div class="form-actions"><button type="button" :disabled="saving" @click="reviewReady = false">Back</button><button type="button" class="primary" :disabled="saving" @click="review">{{ saving ? 'Saving decision…' : 'Confirm decision' }}</button></div></div></form>
      </ApplicationDetail>
    </el-dialog>
  </section>
</template>
<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import ApplicationDetail from '../../components/administration/ApplicationDetail.vue'
import * as api from '../../api/administration.js'
import '../../styles/administration.css'
defineProps({ module: { type: Object, default: null } })
const route = useRoute()
const { statusLabel, typeLabel, dateLabel } = api
const states = ['PENDING', 'RETURNED', 'APPROVED', 'REJECTED']
const user = ref(null), isStaff = computed(() => user.value?.role === 'STAFF')
const loading = ref(true), listBusy = ref(false), error = ref(''), expired = ref(false), success = ref('')
const status = ref(''), search = ref(''), page = ref(1), total = ref(0), applications = ref([]), summary = ref({})
const formOpen = ref(false), editingId = ref(''), saving = ref(false), uploading = ref(false), formError = ref(''), fileInput = ref(null)
const form = reactive({ requestKey: '', applicationType: 'ENROLMENT_CERTIFICATE', purpose: '', documentLanguage: 'ENGLISH', deliveryMethod: 'ELECTRONIC', notes: '', version: null })
const selectedFiles = ref([]), temporaryFiles = ref([])
const unusedFiles = computed(() => temporaryFiles.value.filter(file => !selectedFiles.value.some(selected => selected.id === file.id)))
const detailOpen = ref(false), detailBusy = ref(false), detailError = ref(''), detail = ref(null), reviewError = ref('')
const decision = reactive({ action: 'RETURN', reason: '' })
const reviewReady = ref(false)
let listSequence = 0, detailSequence = 0
async function loadList() {
  const sequence = ++listSequence
  listBusy.value = true; error.value = ''; expired.value = false
  try {
    const [result, counts] = await Promise.all([api.listApplications({ status: status.value, search: search.value, pageNum: page.value }), api.applicationSummary()])
    if (sequence !== listSequence) return
    applications.value = result.records; total.value = result.total; summary.value = counts
  } catch (e) { if (sequence === listSequence) { error.value = e.message; expired.value = e.status === 401 } }
  finally { if (sequence === listSequence) listBusy.value = false }
}
async function initialize() {
  loading.value = true; error.value = ''; expired.value = false
  try { user.value = await api.loadAdministrationSession(); await loadList(); if (route.query.id) await showDetail(String(route.query.id)) }
  catch (e) { error.value = e.message; expired.value = e.status === 401 }
  finally { loading.value = false }
}
async function newApplication() {
  formError.value = ''; success.value = ''; editingId.value = ''
  Object.assign(form, { requestKey: crypto.randomUUID(), applicationType: 'ENROLMENT_CERTIFICATE', purpose: '', documentLanguage: 'ENGLISH', deliveryMethod: 'ELECTRONIC', notes: '', version: null })
  selectedFiles.value = []; formOpen.value = true
  try { temporaryFiles.value = await api.pendingFiles() } catch (e) { formError.value = e.message }
}
function closeForm(done) { if (!saving.value && !uploading.value) done() }
async function upload(event) {
  const file = event.target.files[0]; if (!file) return
  formError.value = ''
  if (selectedFiles.value.length >= 5) { formError.value = 'Attach up to 5 files.'; event.target.value = ''; return }
  if (file.size > 5 * 1024 * 1024) { formError.value = 'Each file must be 5 MB or smaller.'; event.target.value = ''; return }
  uploading.value = true
  try { const saved = await api.uploadFile(file); selectedFiles.value.push(saved); temporaryFiles.value.push(saved) }
  catch (e) { formError.value = e.message }
  finally { uploading.value = false; event.target.value = '' }
}
function removeSelected(file) { selectedFiles.value = selectedFiles.value.filter(f => f.id !== file.id) }
async function deleteUnused(file) {
  formError.value = ''; uploading.value = true
  try { await api.deletePendingFile(file.id); temporaryFiles.value = temporaryFiles.value.filter(f => f.id !== file.id) }
  catch (e) { formError.value = e.message } finally { uploading.value = false }
}
async function save() {
  if (saving.value || uploading.value) return
  formError.value = ''
  if (form.purpose.trim().length < 5) { formError.value = 'Enter a purpose of at least 5 characters.'; return }
  if (form.applicationType === 'TRANSCRIPT' && !selectedFiles.value.length) { formError.value = 'Transcript requests need a supporting file.'; return }
  saving.value = true
  try {
    const payload = { ...form, attachmentIds: selectedFiles.value.map(file => file.id) }
    const saved = editingId.value ? await api.resubmitApplication(editingId.value, payload) : await api.submitApplication(payload)
    formOpen.value = false; success.value = editingId.value ? 'Your revised application has been submitted.' : 'Your application has been submitted.'
    await loadList(); await showDetail(saved.application.id)
  } catch (e) { formError.value = e.message }
  finally { saving.value = false }
}
async function showDetail(id) {
  const sequence = ++detailSequence
  detailOpen.value = true; detailBusy.value = true; detailError.value = ''; detail.value = null; reviewError.value = ''; decision.action = 'RETURN'; decision.reason = ''; reviewReady.value = false
  try { const value = await api.applicationDetail(id); if (sequence === detailSequence) detail.value = value }
  catch (e) { if (sequence === detailSequence) detailError.value = e.message }
  finally { if (sequence === detailSequence) detailBusy.value = false }
}
async function revise() {
  const app = detail.value.application
  editingId.value = app.id
  Object.assign(form, { requestKey: crypto.randomUUID(), applicationType: app.applicationType, purpose: app.purpose, documentLanguage: app.documentLanguage, deliveryMethod: app.deliveryMethod, notes: app.notes || '', version: app.version })
  selectedFiles.value = detail.value.attachments.filter(file => file.active); formError.value = ''; detailOpen.value = false; formOpen.value = true
  try { temporaryFiles.value = await api.pendingFiles() } catch (e) { formError.value = e.message }
}
function prepareReview() {
  reviewError.value = ''
  if (decision.action !== 'APPROVE' && !decision.reason.trim()) { reviewError.value = 'Enter a reason for the student.'; return }
  reviewReady.value = true
}
async function review() {
  if (saving.value) return
  reviewError.value = ''
  if (decision.action !== 'APPROVE' && !decision.reason.trim()) { reviewError.value = 'Enter a reason for the student.'; return }
  saving.value = true
  try { detail.value = await api.reviewApplication(detail.value.application.id, { ...decision, version: detail.value.application.version }); success.value = 'Review decision saved.'; await loadList() }
  catch (e) { reviewError.value = e.message + (e.status === 409 ? ' Close this dialog and open the application again.' : '') }
  finally { saving.value = false }
}
watch(() => route.query.id, id => { if (id && user.value) showDetail(String(id)) })
onMounted(initialize)
</script>
