import request from '../utils/request.js'

export const REPAIR_API_KEY = Symbol.for('smartcampus.m4.api')
export const REPAIR_SESSION_KEY = Symbol.for('smartcampus.m4.session')

export class RepairApiError extends Error {
  constructor(status, message) { super(message); this.name = 'RepairApiError'; this.status = status }
}

export function createRepairsApi(client = request) {
  async function call(method, path, value) {
    try {
      const response = await (method === 'get' ? client.get(path, { params: value }) : client[method](path, value))
      if (response?.code !== '0') {
        throw new RepairApiError(Number(response?.code) > 0 ? Number(response.code) : 500,
          typeof response?.msg === 'string' ? response.msg : 'The service returned an unexpected response.')
      }
      return response.data
    } catch (error) {
      if (error instanceof RepairApiError) throw error
      const status = Number(error.response?.status) || 0
      const fallback = ({ 401: 'Your session has expired. Please sign in again.', 403: 'You do not have permission to access this report.',
        404: 'This report is unavailable or does not belong to you.', 409: 'This report has changed. Refresh and try again.' })[status]
      throw new RepairApiError(status, error.response?.data?.msg || fallback ||
        (status ? 'The service could not complete this request. Please try again.' : 'Unable to connect. Check your connection and try again.'))
    }
  }
  return {
    async session() {
      // Legacy session endpoints use -1, while M4 business endpoints use HTTP 401.
      const [identity, account] = await Promise.all([client.get('/main/loadIdentity'), client.get('/main/loadUserInfo')])
        .catch(error => { throw new RepairApiError(error.response?.status === 401 ? 401 : 0,
          error.response?.status === 401 ? 'Your session has expired. Please sign in again.' : 'Unable to verify your session. Please try again.') })
      if (identity?.code !== '0' || account?.code !== '0' ||
          !['stu', 'dormManager', 'admin'].includes(identity.data) || (typeof account.data?.username !== 'string' || !account.data.username.trim())) {
        throw new RepairApiError(401, 'Your session has expired. Please sign in again.')
      }
      const { password, token, ...user } = account.data
      return { identity: identity.data, user }
    },
    async list(params) {
      const data = await call('get', '/repairs', params)
      if (!Array.isArray(data?.records) || !Number.isSafeInteger(data.total) || data.total < 0) {
        throw new RepairApiError(500, 'The report list could not be read. Please try again.')
      }
      return data
    },
    create: body => call('post', '/repairs', body),
    async staff() {
      const data = await call('get', '/repairs/staff')
      if (!Array.isArray(data) || data.some(item => typeof item.username !== 'string' || typeof item.displayName !== 'string')) {
        throw new RepairApiError(500, 'Staff options could not be read. Please try again.')
      }
      return data.map(({ username, displayName }) => ({ username, displayName }))
    },
    assign: (id, body) => call('put', `/repairs/${encodeURIComponent(id)}/assignment`, body),
    changeStatus: (id, body) => call('put', `/repairs/${encodeURIComponent(id)}/status`, body),
    addProgress: (id, body) => call('post', `/repairs/${encodeURIComponent(id)}/progress`, body),
    detail: id => call('get', `/repairs/${encodeURIComponent(id)}`),
    history: async id => {
      const data = await call('get', `/repairs/${encodeURIComponent(id)}/history`)
      if (!Array.isArray(data)) throw new RepairApiError(500, 'The report history could not be read. Please try again.')
      return data
    },
  }
}
