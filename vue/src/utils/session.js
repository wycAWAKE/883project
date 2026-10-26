import { campusModules } from '../config/modules.js'

export function clearSession(storage) {
  for (const key of ['user', 'identity', 'access-user']) storage.removeItem(key)
}

export function saveSession(storage, user, identity) {
  // Do not persist credentials returned by the legacy API.
  const { password, token, ...account } = user
  storage.setItem('user', JSON.stringify(account))
  storage.setItem('identity', JSON.stringify(identity))
  storage.removeItem('access-user')
}

export function createSessionGuard({ request, storage }) {
  return async to => {
    if (to.name === 'Login') return true
    let reason = 'expired'
    try {
      const [identity, user] = await Promise.all([
        request.get('/main/loadIdentity'), request.get('/main/loadUserInfo'),
      ])
      if (identity.code === '0' && user.code === '0' &&
          ['stu', 'dormManager', 'admin'].includes(identity.data) &&
          typeof user.data?.username === 'string' && user.data.username.trim()) {
        saveSession(storage, user.data, identity.data)
        return true
      }
    } catch (error) {
      reason = [401, 403].includes(error.response?.status) ? 'expired' : 'unavailable'
    }
    clearSession(storage)
    return { name: 'Login', query: { reason, redirect: to.fullPath }, replace: true }
  }
}

export async function signOut(request, storage) {
  const response = await request.get('/main/signOut')
  if (response.code !== '0') throw new Error('Sign-out was not confirmed.')
  clearSession(storage)
}

export function getLoginRedirect(value) {
  if (typeof value !== 'string') return '/home'
  const path = value.split(/[?#]/, 1)[0]
  return path === '/home' || campusModules.some(module => module.path === path) ? value : '/home'
}
