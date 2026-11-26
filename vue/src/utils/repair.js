import { reactive } from 'vue'

export const repairStatuses = [
  { value: 'SUBMITTED', label: 'Submitted' }, { value: 'ASSIGNED', label: 'Assigned' },
  { value: 'IN_PROGRESS', label: 'In progress' }, { value: 'RESOLVED', label: 'Resolved' }, { value: 'CLOSED', label: 'Closed' },
]
export const repairLocations = [
  { value: 'CLASSROOM', label: 'Classroom' }, { value: 'DORM', label: 'Dormitory' },
  { value: 'PUBLIC_AREA', label: 'Public area' }, { value: 'OTHER', label: 'Other' },
]
export const statusLabel = value => repairStatuses.find(item => item.value === value)?.label || 'Unknown status'
export const locationLabel = value => repairLocations.find(item => item.value === value)?.label || 'Other'
export const actionLabel = value => ({ CREATED: 'Report submitted', ASSIGNED: 'Staff assigned', REASSIGNED: 'Staff reassigned',
  STARTED: 'Work started', PROGRESS_ADDED: 'Progress update', RESOLVED: 'Issue resolved', CLOSED: 'Report closed' })[value] || 'Update'
export const emptyRepairForm = () => ({ title: '', description: '', locationType: '', locationText: '' })

export function validTicketId(value) {
  if (typeof value !== 'string' || !/^[1-9]\d{0,18}$/.test(value)) return null
  return value.length < 19 || value <= '9223372036854775807' ? value : null
}
export function validateRepairForm(form) {
  const body = emptyRepairForm(), errors = {}
  for (const [field, max, label] of [['title', 100, 'Title'], ['description', 2000, 'Description'], ['locationText', 200, 'Location']]) {
    body[field] = typeof form[field] === 'string' ? form[field].trim() : ''
    if (!body[field] || body[field].length > max) errors[field] = `${label} is required and must not exceed ${max} characters.`
  }
  body.locationType = form.locationType
  if (!repairLocations.some(item => item.value === body.locationType)) errors.locationType = 'Choose a location type.'
  return { body, errors }
}
export function repairDraftStore(storage, username, now = () => Date.now()) {
  const key = `m4-repair-draft:${username}`
  return {
    read() {
      try {
        const draft = JSON.parse(storage?.getItem(key) || 'null')
        if (!draft || draft.username !== username || !Number.isFinite(draft.savedAt) ||
            now() - draft.savedAt > 2 * 60 * 60 * 1000 || draft.savedAt > now()) return null
        const form = emptyRepairForm()
        for (const field of Object.keys(form)) if (typeof draft.form?.[field] === 'string') form[field] = draft.form[field]
        return form
      } catch { return null }
    },
    save(form) { try { storage?.setItem(key, JSON.stringify({ username, savedAt: now(), form })) } catch { /* Optional storage. */ } },
    clear() { try { storage?.removeItem(key) } catch { /* Optional storage. */ } },
  }
}


// Shared read behaviour keeps request ordering, permissions errors and disposal consistent.
export function createRepairReadModel({ api, onExpired = () => {}, extraParams = () => ({}) }) {
  const state = reactive({ records: [], total: 0, pageNum: 1, pageSize: 10, keyword: '', status: '', listLoading: true, listError: '',
    detailOpen: false, detailLoading: false, detailError: '', detail: null, history: [], detailId: null })
  let listVersion = 0, detailVersion = 0, disposed = false, expired = false
  const isActive = () => !disposed && !expired
  function failed(error) {
    if (error.status === 401 && !expired) {
      expired = true; ++listVersion; ++detailVersion
      state.records = []; state.detail = null; state.history = []; onExpired()
    }
    return error.message || 'The request could not be completed. Please try again.'
  }
  async function loadList() {
    if (!isActive()) return false
    const version = ++listVersion
    state.listLoading = true; state.listError = ''
    try {
      const data = await api.list({ pageNum: state.pageNum, pageSize: state.pageSize, keyword: state.keyword.trim(), status: state.status, ...extraParams() })
      if (!isActive() || version !== listVersion) return false
      state.records = data.records; state.total = data.total
      return true
    } catch (error) {
      if (isActive() && version === listVersion) { state.records = []; state.total = 0; state.listError = failed(error) }
      return false
    } finally { if (isActive() && version === listVersion) state.listLoading = false }
  }
  async function openTicket(rawId) {
    if (!isActive()) return false
    const version = ++detailVersion
    state.detailOpen = true; state.detail = null; state.history = []; state.detailError = ''
    state.detailId = validTicketId(rawId)
    if (!state.detailId) { state.detailLoading = false; state.detailError = 'Choose a valid report ID.'; return false }
    state.detailLoading = true
    try {
      const [ticket, history] = await Promise.all([api.detail(state.detailId), api.history(state.detailId)])
      if (!isActive() || version !== detailVersion) return false
      state.detail = ticket; state.history = history
      return true
    } catch (error) {
      if (isActive() && version === detailVersion) state.detailError = failed(error)
      return false
    } finally { if (isActive() && version === detailVersion) state.detailLoading = false }
  }
  function closeDetail() { ++detailVersion; state.detailOpen = false; state.detailLoading = false; state.detail = null; state.history = []; state.detailError = '' }
  return { state, loadList, openTicket, closeDetail, failed, isActive,
    applyFilters() { state.pageNum = 1; return loadList() },
    changePage(page) { state.pageNum = page; return loadList() },
    changeSize(size) { state.pageSize = Number(size); state.pageNum = 1; return loadList() },
    dispose() { disposed = true; ++listVersion; ++detailVersion },
  }
}

export function createRepairStudentModel({ api, onExpired = () => {}, drafts = null }) {
  const read = createRepairReadModel({ api, onExpired: () => {
    if (Object.values(read.state.form).some(value => value.trim())) drafts?.save(read.state.form)
    onExpired()
  } })
  const recovered = drafts?.read(), state = read.state
  Object.assign(state, { form: recovered || emptyRepairForm(), formErrors: {}, createOpen: Boolean(recovered), submitting: false,
    submitError: '', submitSuccess: '', uncertain: false })
  async function submit() {
    if (state.submitting || !read.isActive()) return null
    const { body, errors } = validateRepairForm(state.form)
    state.formErrors = errors; state.submitError = ''; state.submitSuccess = ''
    if (Object.keys(errors).length) return null
    state.submitting = true; state.uncertain = false
    try {
      const ticket = await api.create(body)
      if (!read.isActive()) return null
      drafts?.clear(); state.form = emptyRepairForm(); state.createOpen = false
      state.submitSuccess = `Report M4-${ticket.id} was submitted successfully.`
      state.keyword = ''; state.status = ''; state.pageNum = 1
      await read.loadList()
      // Do not emit navigation from a page that has expired or been unmounted during refresh.
      return read.isActive() ? ticket : null
    } catch (error) {
      if (read.isActive()) {
        state.uncertain = error.status === 0
        state.submitError = state.uncertain ? 'The connection was interrupted. Check your reports before submitting again.' : read.failed(error)
      }
      return null
    } finally { if (read.isActive()) state.submitting = false }
  }
  return { ...read, submit }
}

export function repairStaffActions(actor, ticket) {
  if (!ticket || !['dormManager', 'admin'].includes(actor?.identity)) return []
  const actions = []
  if (['SUBMITTED', 'ASSIGNED'].includes(ticket.status)) actions.push('assign')
  if (actor.identity === 'admin' || ticket.assigneeUsername === actor.user?.username) {
    if (ticket.status === 'ASSIGNED') actions.push('start')
    if (ticket.status === 'IN_PROGRESS') actions.push('progress', 'resolve')
    if (ticket.status === 'RESOLVED') actions.push('close')
  }
  return actions
}
export const staffActionLabels = { assign: 'Assign staff', start: 'Start work', progress: 'Add progress', resolve: 'Resolve issue', close: 'Close ticket' }
export const staffConfirmLabels = { assign: 'Confirm assignment', start: 'Confirm start', progress: 'Save progress', resolve: 'Confirm resolution', close: 'Confirm closure' }

export function createRepairStaffModel({ api, actor, onExpired = () => {}, drafts = null }) {
  const read = createRepairReadModel({ api, onExpired: () => {
    if (state.operation) drafts?.save({ ticketId: state.detailId, operation: state.operation, note: state.note, assigneeUsername: state.assigneeUsername })
    onExpired()
  }, extraParams: () => actor.identity === 'dormManager' ? { mine: read.state.mine } : {} })
  const state = read.state
  Object.assign(state, { mine: false, staff: [], staffLoading: false, staffError: '', operation: '', note: '', assigneeUsername: '',
    operationVersion: null, operationError: '', requiresReview: false, reviewReady: false, busy: false, notice: '' })
  const actions = () => repairStaffActions(actor, state.detail)
  async function loadStaff() {
    if (!read.isActive() || state.staffLoading) return
    state.staffLoading = true; state.staffError = ''
    try { const options = await api.staff(); if (read.isActive()) state.staff = options }
    catch (error) { if (read.isActive()) state.staffError = read.failed(error) }
    finally { if (read.isActive()) state.staffLoading = false }
  }
  function cancelOperation() {
    if (state.busy) return
    state.operation = ''; state.note = ''; state.assigneeUsername = ''; state.operationVersion = null
    state.operationError = ''; state.requiresReview = false; state.reviewReady = false; drafts?.clear()
  }
  function beginOperation(kind) {
    if (state.busy || state.detailLoading || !actions().includes(kind)) return false
    if (state.operation !== kind) state.note = ''
    state.operation = kind; state.operationVersion = state.detail.version; state.operationError = ''; state.requiresReview = false
    state.assigneeUsername = state.detail.assigneeUsername || ''
    if (kind === 'assign' && !state.staff.length) loadStaff()
    return true
  }
  async function refreshTicket() {
    const id = state.detailId
    const result = id ? await read.openTicket(id) : false
    if (state.operation && state.operationVersion !== state.detail?.version) state.requiresReview = true
    if (state.requiresReview) state.reviewReady = result
    return result
  }
  function acceptRefreshed() {
    if (!state.detail || state.detailLoading || (state.requiresReview && !state.reviewReady) || !actions().includes(state.operation)) return false
    state.operationVersion = state.detail.version; state.requiresReview = false; state.reviewReady = false; state.operationError = ''
    return true
  }
  async function perform() {
    if (state.busy || !read.isActive()) return false
    state.operationError = ''; state.notice = ''
    const kind = state.operation, ticket = state.detail
    if (!ticket || !actions().includes(kind)) { state.operationError = 'This action is no longer available for this report.'; return false }
    if (state.requiresReview || state.operationVersion !== ticket.version) { state.operationError = 'Review the refreshed report before confirming again.'; return false }
    const note = state.note.trim(), max = kind === 'assign' ? 1800 : 2000
    if (note.length > max || (['progress', 'resolve', 'close'].includes(kind) && !note)) {
      state.operationError = `A processing note is ${['progress', 'resolve', 'close'].includes(kind) ? 'required and ' : ''}limited to ${max} characters.`; return false
    }
    if (kind === 'assign' && (!state.staff.some(item => item.username === state.assigneeUsername) || state.assigneeUsername === ticket.assigneeUsername)) {
      state.operationError = 'Choose an available staff member different from the current assignee.'; return false
    }
    const id = String(ticket.id), version = state.operationVersion
    state.busy = true
    try {
      const body = { version, note }
      const result = kind === 'assign' ? await api.assign(id, { ...body, assigneeUsername: state.assigneeUsername })
        : kind === 'progress' ? await api.addProgress(id, body)
          : await api.changeStatus(id, { ...body, targetStatus: { start: 'IN_PROGRESS', resolve: 'RESOLVED', close: 'CLOSED' }[kind] })
      if (!read.isActive()) return false
      drafts?.clear(); state.detail = result; state.notice = 'The report was updated successfully.'
      state.busy = false; cancelOperation(); state.busy = true
      await Promise.all([read.loadList(), read.openTicket(id)])
      return read.isActive()
    } catch (error) {
      if (!read.isActive()) return false
      state.operationError = read.failed(error)
      if (error.status === 409 || error.status === 403) {
        state.requiresReview = true; state.reviewReady = false; state.operationVersion = null
        const results = await Promise.all([read.loadList(), read.openTicket(id)])
        state.reviewReady = results[1]
      } else if (error.status === 404) {
        state.notice = 'This report is no longer available.'; read.closeDetail(); await read.loadList()
      } else if (error.status === 0) {
        state.reviewReady = false; state.requiresReview = true; state.operationVersion = null
        state.operationError = 'Connection interrupted. Refresh the report and check its history before confirming again.'
      }
      return false
    } finally { if (read.isActive()) state.busy = false }
  }
  return { ...read, actions, loadStaff, beginOperation, cancelOperation, refreshTicket, acceptRefreshed, perform,
    async openTicket(id) {
      if (state.busy) return false
      const recovered = drafts?.read()
      cancelOperation()
      const result = await read.openTicket(id)
      if (result && recovered && recovered.ticketId === id) {
        state.operation = recovered.operation; state.note = recovered.note; state.assigneeUsername = recovered.assigneeUsername
        state.requiresReview = true; state.reviewReady = true
        state.operationError = 'A processing draft was restored. Review the current report before confirming.'
        if (state.operation === 'assign') loadStaff()
      }
      return result
    },
    closeDetail() { if (!state.busy) { cancelOperation(); read.closeDetail() } },
  }
}


export function repairStaffDraftStore(storage, actor, now = () => Date.now()) {
  const key = `m4-staff-draft:${actor.identity}:${actor.user.username}`
  return {
    read() {
      try {
        const draft = JSON.parse(storage?.getItem(key) || 'null')
        if (!draft || !Number.isFinite(draft.savedAt) || draft.savedAt > now() || now()-draft.savedAt > 7200000 ||
            !validTicketId(draft.ticketId) || !Object.hasOwn(staffActionLabels,draft.operation) ||
            typeof draft.note !== 'string' || typeof draft.assigneeUsername !== 'string') return null
        return draft
      } catch { return null }
    },
    save(value) { try { storage?.setItem(key,JSON.stringify({ ...value, savedAt:now() })) } catch { /* Optional storage. */ } },
    clear() { try { storage?.removeItem(key) } catch { /* Optional storage. */ } },
  }
}
