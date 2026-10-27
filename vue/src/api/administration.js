import request from '../utils/request.js'
let csrfToken = ''

async function unwrap(operation) {
  try {
    const response = await operation
    if (response.code !== '0') throw new Error(response.msg || 'The operation failed.')
    return response.data
  } catch (error) {
    const problem = new Error(error.response?.data?.msg || error.message || 'Cannot connect. Please try again.')
    problem.status = error.response?.status
    if (problem.status === 401) csrfToken = ''
    throw problem
  }
}
export async function loadAdministrationSession() {
  const data = await unwrap(request.get('/applications/session'))
  csrfToken = data.csrfToken
  return data.user
}
async function write(method, url, data) {
  if (!csrfToken) await loadAdministrationSession()
  return unwrap(request({ method, url, data, headers: { 'X-CSRF-Token': csrfToken } }))
}
export const listApplications = params => unwrap(request.get('/applications', { params }))
export const applicationSummary = () => unwrap(request.get('/applications/summary'))
export const applicationDetail = id => unwrap(request.get(`/applications/${encodeURIComponent(id)}`))
export const submitApplication = data => write('post', '/applications', data)
export const resubmitApplication = (id, data) => write('put', `/applications/${encodeURIComponent(id)}/resubmit`, data)
export const reviewApplication = (id, data) => write('post', `/applications/${encodeURIComponent(id)}/decision`, data)
export const pendingFiles = () => unwrap(request.get('/attachments/pending'))
export const deletePendingFile = id => write('delete', `/attachments/${encodeURIComponent(id)}`)
export function uploadFile(file) {
  const data = new FormData()
  data.append('file', file)
  return write('post', '/attachments', data)
}
export async function downloadFile(file) {
  try {
    const blob = await request.get(`/attachments/${encodeURIComponent(file.id)}/download`, { responseType: 'blob' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url; link.download = file.filename; link.click()
    setTimeout(() => URL.revokeObjectURL(url), 1000)
  } catch (error) {
    let message = 'Could not download this file. Your session or permission may have changed.'
    if (error.response?.data instanceof Blob) {
      try { message = JSON.parse(await error.response.data.text()).msg || message } catch { /* Keep readable fallback. */ }
    }
    throw new Error(message)
  }
}
export const listNotifications = params => unwrap(request.get('/notifications', { params }))
export const unreadCount = () => unwrap(request.get('/notifications/unread-count'))
export const markNotificationRead = id => write('put', `/notifications/${encodeURIComponent(id)}/read`)
export const markAllNotificationsRead = () => write('put', '/notifications/read-all')
export const statusLabel = state => ({ PENDING: 'Pending review', RETURNED: 'Returned', APPROVED: 'Approved', REJECTED: 'Rejected' })[state] || state
export const typeLabel = type => ({ ENROLMENT_CERTIFICATE: 'Enrolment certificate', TRANSCRIPT: 'Transcript' })[type] || type
export const actionLabel = action => ({ SUBMIT: 'Submitted', RESUBMIT: 'Resubmitted', RETURN: 'Returned', APPROVE: 'Approved', REJECT: 'Rejected' })[action] || action
export function dateLabel(value) { return value ? value.replace('T', ' ').slice(0, 19) : '' }
export function readSnapshot(history) {
  try { return JSON.parse(history.snapshotJson || '{}') } catch { return {} }
}
