import test from 'node:test'
import assert from 'node:assert/strict'
import { createSessionGuard, saveSession, clearSession, signOut, getLoginRedirect } from '../src/utils/session.js'

function storage(initial = {}) {
  const values = new Map(Object.entries(initial))
  return { getItem: key => values.get(key) ?? null, setItem: (key, value) => values.set(key, value), removeItem: key => values.delete(key) }
}
const destination = { fullPath: '/bookings', name: 'bookings' }
function serverSession(identity = 'stu', user = { username: 'student1', name: 'Alex', password: 'private' }) {
  return { get: async path => ({ code: '0', data: path === '/main/loadIdentity' ? identity : user }) }
}

test('protected navigation rejects an expired server Session despite stale cached credentials', async () => {
  const cache = storage({ user: '{"username":"old"}', identity: '"admin"', 'access-user': '{"token":"old"}', preference: 'keep' })
  const guard = createSessionGuard({ request: { get: async () => ({ code: '-1', msg: '加载失败' }) }, storage: cache })
  assert.deepEqual(await guard(destination), { name: 'Login', query: { reason: 'expired', redirect: '/bookings' }, replace: true })
  assert.equal(cache.getItem('user'), null)
  assert.equal(cache.getItem('identity'), null)
  assert.equal(cache.getItem('access-user'), null)
  assert.equal(cache.getItem('preference'), 'keep')
})

test('a valid server Session restores a new tab without storing account passwords', async () => {
  const cache = storage()
  const guard = createSessionGuard({ request: serverSession(), storage: cache })
  assert.equal(await guard(destination), true)
  assert.deepEqual(JSON.parse(cache.getItem('user')), { username: 'student1', name: 'Alex' })
  assert.equal(JSON.parse(cache.getItem('identity')), 'stu')
})

test('all existing login roles remain supported and inconsistent server data is rejected', async () => {
  for (const role of ['stu', 'dormManager', 'admin']) {
    assert.equal(await createSessionGuard({ request: serverSession(role), storage: storage() })(destination), true)
  }
  for (const [role, user] of [['unknown', { username: 'x' }], ['stu', null], ['stu', {}], ['stu', { username: '' }]]) {
    assert.equal((await createSessionGuard({ request: serverSession(role, user), storage: storage() })(destination)).query.reason, 'expired')
  }
})

test('a network error blocks protected navigation and reports availability rather than using cache', async () => {
  const guard = createSessionGuard({ request: { get: async () => { throw new Error('Network Error') } }, storage: storage({ user: '{}' }) })
  const result = await guard(destination)
  assert.equal(result.name, 'Login')
  assert.equal(result.query.reason, 'unavailable')
})

test('HTTP authentication failure redirects to sign-in while the sign-in page makes no Session requests', async () => {
  const guard = createSessionGuard({ request: { get: async () => { throw { response: { status: 401 } } } }, storage: storage() })
  assert.equal((await guard(destination)).query.reason, 'expired')
  assert.equal(await guard({ name: 'Login', fullPath: '/Login' }), true)
})

test('saving an account removes password/token fields and stale token cache without changing the response', () => {
  const cache = storage({ 'access-user': 'obsolete' })
  const user = Object.freeze({ username: 'alex', password: 'secret', token: 'obsolete', name: 'Alex' })
  saveSession(cache, user, 'stu')
  assert.deepEqual(JSON.parse(cache.getItem('user')), { username: 'alex', name: 'Alex' })
  assert.equal(cache.getItem('access-user'), null)
  assert.equal(user.password, 'secret')
  clearSession(cache)
  assert.equal(cache.getItem('identity'), null)
})

test('logout clears auth state only after the server confirms success', async () => {
  const cache = storage({ user: '{}', identity: '"stu"', theme: 'keep' })
  let confirm
  const pending = signOut({ get: () => new Promise(resolve => { confirm = resolve }) }, cache)
  assert.equal(cache.getItem('user'), '{}')
  confirm({ code: '0' })
  await pending
  assert.equal(cache.getItem('user'), null)
  assert.equal(cache.getItem('theme'), 'keep')
})

test('failed logout preserves the local account and rejects instead of claiming success', async () => {
  for (const request of [{ get: async () => { throw new Error('Network Error') } }, { get: async () => ({ code: '-1' }) }]) {
    const cache = storage({ user: '{}' })
    await assert.rejects(signOut(request, cache))
    assert.equal(cache.getItem('user'), '{}')
  }
})

test('login restores a module deep link but rejects external, legacy and malformed destinations', () => {
  assert.equal(getLoginRedirect('/bookings?date=2026-11-01#slots'), '/bookings?date=2026-11-01#slots')
  for (const value of ['https://example.com', '//example.com', '/stuInfo', '/Login', undefined, ['/faq']]) {
    assert.equal(getLoginRedirect(value), '/home')
  }
})
