import test, { beforeEach, afterEach } from 'node:test'
import assert from 'node:assert/strict'
import { effectScope, createSSRApp, h } from 'vue'
import { renderToString } from 'vue/server-renderer'
import { createServer } from 'vite'
import request from '../src/utils/request.js'
import { askFaq, chatFaq, clearFaqConversation } from '../src/api/faq.js'
import { useFaq } from '../src/composables/useFaq.js'

const originalAdapter = request.defaults.adapter
const originalStorage = Object.getOwnPropertyDescriptor(globalThis, 'localStorage')
const conversationId = '16b71f8f-413f-4b9c-a3f8-85cc6ec8f09a'
const answer = Object.freeze({ conversationId, matched: true, faqId: 1, answer: 'Stored test answer.',
  sourceTitle: 'Test source', sourceUrl: 'https://jwc.ccnu.edu.cn/faq', score: 0.7, guidance: null,
  mode: 'extractive', notice: 'AI generation is unavailable. Showing the stored FAQ answer.',
  sources: [{ faqId: 1, sourceTitle: 'Test source', sourceUrl: 'https://jwc.ccnu.edu.cn/faq' }] })
const ok = data => ({ code: '0', msg: 'Success', data })
let calls, chatAdapter, clearAdapter
beforeEach(() => {
  calls = []
  clearAdapter = async config => { calls.push(config); return { data: ok(null), status: 200, headers: {}, config } }
  request.defaults.adapter = config => config.method === 'delete' ? clearAdapter(config) : chatAdapter(config)
  chatAdapter = async config => {
    calls.push(config)
    return { data: ok(answer), status: 200, headers: {}, config }
  }
})
afterEach(() => {
  request.defaults.adapter = originalAdapter
  if (originalStorage) Object.defineProperty(globalThis, 'localStorage', originalStorage)
  else delete globalThis.localStorage
})
function page(t) {
  const scope = effectScope()
  const state = scope.run(() => useFaq())
  t.after(async () => { scope.stop(); await new Promise(setImmediate) })
  return { state, scope }
}
function respond(data) {
  chatAdapter = async config => {
    calls.push(config)
    return { data, status: 200, headers: {}, config }
  }
}
function fail(error) {
  chatAdapter = async config => { calls.push(config); throw error }
}

test('FAQ alone uses a 45-second budget, existing proxy and Cookie with only this question', async () => {
  const result = await askFaq('alpha')
  assert.equal(result.code, '0')
  assert.equal(result.data.answer, 'Stored test answer.')
  assert.equal(calls[0].baseURL, '/api')
  assert.equal(calls[0].url, '/faq/ask')
  assert.equal(calls[0].method, 'post')
  assert.equal(calls[0].withCredentials, true)
  assert.equal(calls[0].timeout, 45000)
  assert.equal(request.defaults.timeout, 5000)
  assert.deepEqual(JSON.parse(calls[0].data), { question: 'alpha' })
})

test('validation trims Unicode whitespace and applies the UTF-16 limit before sending', async t => {
  const { state } = page(t)
  for (const value of ['', '\u3000\u00a0\u0085', 'x'.repeat(1001), '😀'.repeat(501)]) {
    state.question.value = value
    await state.submit()
    assert.match(state.error.value, /between 1 and 1000/)
  }
  assert.equal(calls.length, 0)
  for (const value of ['\u3000a\u0085', 'x'.repeat(1000), '😀'.repeat(500)]) {
    state.question.value = value
    await state.submit()
    assert.equal(state.error.value, '')
  }
  assert.equal(calls.length, 3)
  assert.deepEqual(JSON.parse(calls[0].data), { question: 'a', conversationId: null })
})

test('a pending submission stays loading and ignores duplicate submits', async t => {
  const { state } = page(t)
  let complete
  chatAdapter = config => {
    calls.push(config)
    return new Promise(resolve => { complete = () => resolve({ data: ok(answer), status: 200, headers: {}, config }) })
  }
  state.question.value = 'alpha'
  const pending = state.submit()
  assert.equal(state.loading.value, true)
  await state.submit()
  assert.equal(calls.length, 1)
  complete()
  await pending
  assert.equal(state.loading.value, false)
  assert.equal(state.history.value[0].question, 'alpha')
  assert.equal(state.history.value[0].result.answer, 'Stored test answer.')
  assert.equal(state.question.value, '')
  assert.equal(state.pendingQuestion.value, '')
})

test('history keeps only five page-memory results and never joins the next request', async t => {
  const { state } = page(t)
  for (let i = 1; i <= 6; i++) {
    state.question.value = `Question ${i}`
    await state.submit()
  }
  assert.deepEqual(state.history.value.map(row => row.question),
    ['Question 2', 'Question 3', 'Question 4', 'Question 5', 'Question 6'])
  assert.deepEqual(JSON.parse(calls[5].data), { question: 'Question 6', conversationId })
  assert.equal(answer.answer, 'Stored test answer.')
  await state.newConversation()
  assert.equal(state.history.value.length, 0)
})

test('no match produces guidance and discards any stray answer or source', async t => {
  const { state } = page(t)
  respond(ok({ conversationId, matched: false, mode: 'no_match', sources: [], answer: 'must not be shown', sourceUrl: 'https://example.com', score: 0.2 }))
  state.question.value = 'volcano eruption'
  await state.submit()
  assert.deepEqual({ ...state.history.value[0].result }, {
    matched: false, mode: 'no_match', sources: [], notice: null,
    guidance: 'No matching answer was found. Please contact student support.',
  })
})

test('clarification keeps the conversation ready for an explicit reply and projects fixed guidance', async t => {
  const { state } = page(t)
  respond(ok({ conversationId, matched: false, mode: 'clarify', answer: null, sources: [],
    guidance: 'private upstream text https://evil.example', notice: null }))
  state.question.value = 'What is its phone number?'
  await state.submit()
  assert.equal(state.error.value, '')
  assert.equal(state.history.value[0].result.mode, 'clarify')
  assert.equal(state.history.value[0].result.guidance,
    'Please name the campus service, department or document you mean, and ask a complete question.')
  assert.deepEqual(state.history.value[0].result.sources, [])
  respond(ok(answer))
  state.question.value = 'Undergraduate course selection contact'
  await state.submit()
  assert.equal(state.history.value.length, 2)
  assert.deepEqual(JSON.parse(calls[1].data), { question: 'Undergraduate course selection contact', conversationId })
})

test('clarification with facts or sources is rejected instead of shown as a successful turn', async t => {
  const { state } = page(t)
  for (const data of [{ ...answer, mode: 'clarify' },
    { conversationId, matched: false, mode: 'clarify', answer: null, sources: answer.sources, notice: null }]) {
    respond(ok(data))
    state.question.value = 'What is it?'
    await state.submit()
    assert.match(state.error.value, /Unable to/)
    assert.equal(state.history.value.length, 0)
    await state.newConversation()
  }
})

test('HTTP errors are distinct, sanitized, retain input and do not auto-retry', async t => {
  const { state } = page(t)
  for (const [status, expected] of [[400, /between 1 and 1000/], [403, /permission/], [503, /temporarily unavailable/]]) {
    fail({ response: { status, data: { msg: 'private SQL secret' } } })
    state.question.value = 'keep this question'
    await state.submit()
    assert.match(state.error.value, expected)
    assert.doesNotMatch(state.error.value, /private|SQL|secret/)
    assert.equal(state.question.value, 'keep this question')
    assert.equal(state.history.value.length, 0)
    assert.equal(state.needsSignIn.value, false)
  }
  assert.equal(calls.length, 3)
  respond(ok(answer))
  await state.submit()
  assert.equal(state.error.value, '')
  assert.equal(state.history.value.length, 1)
})

test('network and timeout failures have safe messages and restore submit availability', async t => {
  const { state } = page(t)
  for (const [error, expected] of [[{ code: 'ERR_NETWORK' }, /connect/],
    [{ code: 'ECONNABORTED' }, /timed out/], [{ code: 'ETIMEDOUT' }, /timed out/],
    [new Error('private diagnostics'), /Unable to/]]) {
    fail(error)
    state.question.value = 'alpha'
    await state.submit()
    assert.match(state.error.value, expected)
    assert.equal(state.loading.value, false)
    assert.equal(state.history.value.length, 0)
  }
  assert.equal(calls.length, 4)
})

test('expired Session clears stale credentials and results and requires sign-in', async t => {
  const values = new Map([['user', '{}'], ['identity', '"stu"'], ['access-user', 'old'], ['theme', 'keep']])
  Object.defineProperty(globalThis, 'localStorage', { configurable: true, value: {
    removeItem: key => values.delete(key), setItem: () => assert.fail('History must not be persisted'),
  } })
  const { state } = page(t)
  state.question.value = 'alpha'
  await state.submit()
  fail({ response: { status: 401, data: { msg: 'private diagnostics' } } })
  state.question.value = 'another question'
  await state.submit()
  assert.equal(state.needsSignIn.value, true)
  assert.match(state.error.value, /sign in again/i)
  assert.equal(state.history.value.length, 0)
  assert.deepEqual([...values], [['theme', 'keep']])
  await state.submit()
  assert.equal(calls.length, 2)
})

test('business authentication errors and blocked browser storage cannot leave a false success', async t => {
  Object.defineProperty(globalThis, 'localStorage', { configurable: true, get() { throw new Error('blocked') } })
  const { state } = page(t)
  respond({ code: 'FAQ_UNAUTHENTICATED', msg: 'private details' })
  state.question.value = 'alpha'
  await state.submit()
  assert.equal(state.needsSignIn.value, true)
  assert.match(state.error.value, /sign in again/i)
  assert.equal(state.loading.value, false)
})

test('malformed success envelopes and business failures never display an answer', async t => {
  const { state } = page(t)
  for (const payload of [null, 'broken', { code: 0, data: answer }, { code: '0' },
    ok({ matched: 'true', answer: 'bad' }), ok({ ...answer, answer: ' ' }),
    ok({ ...answer, sources: null }), { code: '-1', msg: 'private server details' }]) {
    respond(payload)
    state.question.value = 'alpha'
    await state.submit()
    assert.match(state.error.value, /Unable to/)
    assert.doesNotMatch(state.error.value, /private/)
    assert.equal(state.history.value.length, 0)
    await state.newConversation()
  }
  assert.equal(calls.filter(call => call.method === 'post').length, 8)
})

test('leaving the page isolates in-flight work and discards history even if a late response arrives', async t => {
  const { state, scope } = page(t)
  state.question.value = 'first'
  await state.submit()
  let complete
  chatAdapter = config => {
    return new Promise(resolve => { complete = () => resolve({ data: ok(answer), status: 200, headers: {}, config }) })
  }
  state.question.value = 'second'
  const pending = state.submit()
  await new Promise(setImmediate)
  scope.stop()
  assert.equal(state.history.value.length, 0)
  complete()
  await pending
  assert.equal(state.history.value.length, 0)
  assert.equal(state.error.value, '')
  assert.equal(state.loading.value, false)
})

test('generated answers project all actual sources in server order and discard private fields', async t => {
  const { state } = page(t)
  const sources = [{ faqId: 5, sourceTitle: 'Hall', sourceUrl: 'https://jwc.ccnu.edu.cn/hall', secret: 'hidden' },
    { faqId: 4, sourceTitle: 'Courses', sourceUrl: 'https://jwc.ccnu.edu.cn/courses' }]
  respond(ok({ ...answer, mode: 'generated', answer: 'Hall [FAQ:5]. Courses [FAQ:4].',
    sources, notice: null, secret: 'hidden', sourceTitle: 'legacy must not replace list' }))
  state.question.value = 'Contacts'
  await state.submit()
  assert.deepEqual(JSON.parse(JSON.stringify(state.history.value[0].result)), {
    matched: true, mode: 'generated', answer: 'Hall [FAQ:5]. Courses [FAQ:4].', notice: null,
    sources: sources.map(({ faqId, sourceTitle, sourceUrl }) => ({ faqId, sourceTitle, sourceUrl })),
  })
})

test('inconsistent modes or malformed citation lists never become successful chat turns', async t => {
  const { state } = page(t)
  for (const data of [{ ...answer, mode: undefined }, { ...answer, mode: 'invented' },
    { ...answer, mode: 'no_match' }, { ...answer, matched: false }, { ...answer, sources: [] },
    { ...answer, sources: [...answer.sources, ...answer.sources] },
    { ...answer, sources: [{ faqId: -1, sourceTitle: 'Bad' }] },
    { ...answer, sources: [{ faqId: 1, sourceTitle: '' }] },
    { ...answer, sources: [{ faqId: '1', sourceTitle: 'Bad' }] },
    { ...answer, notice: null }, { ...answer, mode: 'generated', notice: 'private error' }]) {
    respond(ok(data))
    state.question.value = 'alpha'
    await state.submit()
    assert.match(state.error.value, /Unable to/)
    assert.equal(state.history.value.length, 0)
    assert.equal(state.question.value, 'alpha')
    await state.newConversation()
  }
  assert.equal(calls.filter(call => call.method === 'post').length, 11)
})

test('stop ends UI waiting but preserves draft and turns until the server settles', async t => {
  const { state } = page(t)
  state.question.value = 'completed'
  await state.submit()
  let complete
  chatAdapter = config => new Promise(resolve => { complete = () => resolve({ data: ok(answer), status: 200, headers: {}, config }) })
  state.question.value = 'stopped question'
  const pending = state.submit()
  await new Promise(setImmediate)
  state.stop()
  assert.equal(state.loading.value, false)
  assert.equal(state.processing.value, true)
  assert.equal(state.question.value, 'stopped question')
  assert.match(state.status.value, /Stopped waiting/)
  assert.equal(state.history.value.length, 1)
  const before = calls.length
  await state.submit()
  assert.equal(calls.length, before)
  complete()
  await pending
  assert.equal(state.processing.value, false)
  assert.equal(state.history.value.length, 1)
  assert.equal(state.question.value, 'stopped question')
  assert.equal(state.conversationId.value, conversationId)
  respond(ok(answer))
  await state.submit()
  assert.equal(state.history.value.length, 2)
})

test('new conversation waits for the old request and clears the server before resetting local state', async t => {
  const { state } = page(t)
  state.question.value = 'completed'
  await state.submit()
  let reject
  chatAdapter = config => { calls.push(config); return new Promise((_, no) => { reject = no }) }
  state.question.value = 'obsolete'
  const old = state.submit()
  await new Promise(setImmediate)
  const reset = state.newConversation()
  assert.equal(state.resetting.value, true)
  assert.equal(state.history.value.length, 1)
  assert.equal(calls.filter(c => c.method === 'delete').length, 0)
  await state.submit()
  reject({ response: { status: 503 } })
  await old
  await reset
  assert.equal(calls.at(-1).method, 'delete')
  assert.equal(calls.at(-1).url, '/faq/chat/current')
  assert.equal(state.history.value.length, 0)
  assert.equal(state.question.value, '')
  assert.equal(state.conversationId.value, null)
  assert.equal(state.resetting.value, false)
  assert.equal(state.error.value, '')
})

test('new conversation cannot clear the expired authentication gate', async t => {
  const { state } = page(t)
  fail({ response: { status: 401 } })
  state.question.value = 'alpha'
  await state.submit()
  state.newConversation()
  assert.equal(state.needsSignIn.value, true)
  assert.match(state.error.value, /sign in again/i)
  state.question.value = 'retry'
  await state.submit()
  assert.equal(calls.length, 1)
})

test('chat and clear use the existing Cookie proxy with a FAQ-only timeout', async () => {
  await chatFaq('first', null)
  await chatFaq('second', conversationId)
  await clearFaqConversation()
  assert.deepEqual(calls.map(c => [c.method, c.url]), [
    ['post', '/faq/chat'], ['post', '/faq/chat'], ['delete', '/faq/chat/current'],
  ])
  assert.deepEqual(JSON.parse(calls[0].data), { question: 'first', conversationId: null })
  assert.deepEqual(JSON.parse(calls[1].data), { question: 'second', conversationId })
  for (const call of calls) {
    assert.equal(call.baseURL, '/api')
    assert.equal(call.withCredentials, true)
    assert.equal(call.timeout, 45000)
  }
  assert.equal(request.defaults.timeout, 5000)
})

test('the page stores only the returned UUID and reuses it without uploading displayed history', async t => {
  const { state } = page(t)
  state.question.value = 'first'
  await state.submit()
  assert.equal(state.conversationId.value, conversationId)
  state.question.value = 'second'
  await state.submit()
  assert.deepEqual(JSON.parse(calls[0].data), { question: 'first', conversationId: null })
  assert.deepEqual(JSON.parse(calls[1].data), { question: 'second', conversationId })
})

test('missing, invalid or mismatched response IDs never display success and require a reset', async t => {
  const { state } = page(t)
  for (const value of [undefined, 12, '', '1-1-1-1-1']) {
    respond(ok({ ...answer, conversationId: value }))
    state.question.value = 'draft'
    await state.submit()
    assert.equal(state.history.value.length, 0)
    assert.equal(state.conversationId.value, null)
    assert.equal(state.needsNewConversation.value, true)
    const count = calls.length
    await state.submit()
    assert.equal(calls.length, count)
    await state.newConversation()
  }
  respond(ok(answer))
  state.question.value = 'first'
  await state.submit()
  respond(ok({ ...answer, conversationId: '4cc9c5f1-3296-4e99-9c46-18ae6ae5704e' }))
  state.question.value = 'second'
  await state.submit()
  assert.equal(state.history.value.length, 1)
  assert.equal(state.needsNewConversation.value, true)
})

test('404 retains the draft and results and requires an acknowledged new conversation', async t => {
  const { state } = page(t)
  state.question.value = 'first'
  await state.submit()
  fail({ response: { status: 404, data: { msg: 'secret details' } } })
  state.question.value = 'keep draft'
  await state.submit()
  assert.match(state.error.value, /new conversation/i)
  assert.doesNotMatch(state.error.value, /secret/)
  assert.equal(state.needsNewConversation.value, true)
  assert.equal(state.history.value.length, 1)
  assert.equal(state.question.value, 'keep draft')
  const count = calls.length
  await state.submit()
  assert.equal(calls.length, count)
  await state.newConversation()
  respond(ok(answer))
  state.question.value = 'fresh'
  await state.submit()
  assert.equal(JSON.parse(calls.at(-1).data).conversationId, null)
})

test('409 is safe and allows an explicit retry without creating a different conversation', async t => {
  const { state } = page(t)
  state.question.value = 'first'
  await state.submit()
  respond({ code: 'FAQ_CONVERSATION_BUSY', msg: 'private diagnostics' })
  state.question.value = 'retry draft'
  await state.submit()
  assert.match(state.error.value, /processing/i)
  assert.equal(state.question.value, 'retry draft')
  assert.equal(state.conversationId.value, conversationId)
  assert.equal(state.needsNewConversation.value, false)
  assert.equal(state.history.value.length, 1)
  assert.equal(calls.length, 2)
  respond(ok(answer))
  await state.submit()
  assert.equal(JSON.parse(calls.at(-1).data).conversationId, conversationId)
  assert.equal(state.history.value.length, 2)
})

test('a failed or malformed clear never claims deletion and blocks sends until manual retry succeeds', async t => {
  const { state } = page(t)
  state.question.value = 'first'
  await state.submit()
  state.question.value = 'preserved draft'
  for (const failClear of [async () => { throw { response: { status: 503, data: { msg: 'secret' } } } },
    async config => ({ data: ok({ unexpected: true }), status: 200, headers: {}, config })]) {
    clearAdapter = failClear
    await state.newConversation()
    assert.equal(state.history.value.length, 1)
    assert.equal(state.question.value, 'preserved draft')
    assert.equal(state.conversationId.value, conversationId)
    assert.equal(state.needsNewConversation.value, true)
    assert.match(state.error.value, /new conversation/i)
    assert.doesNotMatch(state.error.value, /secret/)
    const count = calls.length
    await state.submit()
    assert.equal(calls.length, count)
  }
  clearAdapter = async config => ({ data: ok(null), status: 200, headers: {}, config })
  await state.newConversation()
  assert.equal(state.history.value.length, 0)
  assert.equal(state.conversationId.value, null)
  assert.equal(state.needsNewConversation.value, false)
})

test('401 from clear preserves the authentication gate and removes prior answers and IDs', async t => {
  const { state } = page(t)
  state.question.value = 'first'
  await state.submit()
  clearAdapter = async () => { throw { response: { status: 401 } } }
  await state.newConversation()
  assert.equal(state.needsSignIn.value, true)
  assert.equal(state.history.value.length, 0)
  assert.equal(state.conversationId.value, null)
  assert.match(state.error.value, /sign in again/i)
})

test('clearing is single-flight and a late success cannot reappear after reset', async t => {
  const { state } = page(t)
  let finishChat, finishClear
  chatAdapter = config => { calls.push(config); return new Promise(resolve => { finishChat = () =>
    resolve({ data: ok(answer), status: 200, headers: {}, config }) }) }
  clearAdapter = config => { calls.push(config); return new Promise(resolve => { finishClear = () =>
    resolve({ data: ok(null), status: 200, headers: {}, config }) }) }
  state.question.value = 'old'
  const old = state.submit()
  await new Promise(setImmediate)
  const reset = state.newConversation()
  const duplicate = state.newConversation()
  finishChat()
  await old
  await new Promise(setImmediate)
  assert.equal(state.history.value.length, 0)
  assert.equal(state.conversationId.value, null)
  assert.equal(state.resetting.value, true)
  assert.equal(calls.filter(c => c.method === 'delete').length, 1)
  await state.submit()
  finishClear()
  await Promise.all([reset, duplicate])
  assert.equal(state.resetting.value, false)
  assert.equal(state.history.value.length, 0)
})

test('leaving and immediately reentering orders old cleanup before the new page POST', async t => {
  const oldPage = page(t)
  oldPage.state.question.value = 'old page'
  await oldPage.state.submit()
  let finishClear
  clearAdapter = config => { calls.push(config); return new Promise(resolve => { finishClear = () =>
    resolve({ data: ok(null), status: 200, headers: {}, config }) }) }
  oldPage.scope.stop()
  const newPage = page(t)
  newPage.state.question.value = 'new page'
  const pending = newPage.state.submit()
  await new Promise(setImmediate)
  assert.equal(calls.filter(c => c.method === 'post').length, 1)
  assert.equal(calls.at(-1).method, 'delete')
  finishClear()
  clearAdapter = async config => ({ data: ok(null), status: 200, headers: {}, config })
  await pending
  assert.deepEqual(calls.map(c => c.method), ['post', 'delete', 'post'])
  assert.equal(newPage.state.history.value.length, 1)
  assert.equal(oldPage.state.history.value.length, 0)
})

test('the actual answer card escapes text, allows only safe sources, and hides no-match payloads', async () => {
  const server = await createServer({ server: { middlewareMode: true, watch: null, hmr: false, ws: false },
    optimizeDeps: { noDiscovery: true, include: [] }, appType: 'custom' })
  try {
    const { default: Card } = await server.ssrLoadModule('/src/components/faq/FaqAnswer.vue')
    const render = result => renderToString(createSSRApp({ render: () => h(Card, {
      entry: { id: 1, question: '<script>bad()</script>', result },
    }) }))
    let html = await render({ ...answer, answer: '<img src=x onerror=bad()>',
      sources: [{ ...answer.sources[0], sourceTitle: '<b>source</b>' }] })
    assert.match(html, /Stored answer/)
    assert.match(html, /&lt;img/)
    assert.match(html, /&lt;script/)
    assert.match(html, /&lt;b&gt;source/)
    assert.doesNotMatch(html, /<img|<script|<b>source/)
    assert.match(html, /href="https:\/\/jwc\.ccnu\.edu\.cn\/faq"/)
    assert.match(html, /target="_blank"/)
    assert.match(html, /rel="noopener noreferrer"/)
    assert.match(html, /AI generation is unavailable/)
    html = await render({ ...answer, mode: 'generated', notice: null,
      answer: 'First [FAQ:1]. Second [FAQ:4].', sources: [...answer.sources,
        { faqId: 4, sourceTitle: 'Second source', sourceUrl: 'https://ccnu.edu.cn/second' }] })
    assert.match(html, /AI-generated answer/)
    assert.match(html, /\[FAQ:1\]/)
    assert.match(html, /\[FAQ:4\]/)
    assert.match(html, /Second source/)
    assert.equal((html.match(/target="_blank"/g) || []).length, 2)
    assert.doesNotMatch(html, /Stored answer|AI generation is unavailable/)
    for (const url of [null, '', '/relative', '//example.com', 'javascript:alert(1)',
      'data:text/html,bad', 'https://user:pass@example.com', 'https://example.com/bad path']) {
      html = await render({ ...answer, sources: [{ ...answer.sources[0], sourceUrl: url }] })
      assert.doesNotMatch(html, /href=/)
      assert.match(html, /Source link unavailable/)
    }
    html = await render({ matched: false, answer: 'secret answer', sourceUrl: 'https://example.com',
      guidance: 'No matching answer was found. Please contact student support.' })
    assert.match(html, /No matching answer/)
    assert.doesNotMatch(html, /secret answer|href=/)
    html = await render({ matched: false, mode: 'clarify', sources: [],
      guidance: 'Please name the campus service, department or document you mean, and ask a complete question.' })
    assert.match(html, /Please clarify/)
    assert.doesNotMatch(html, /No matching answer|Sources|href=/)
  } finally { await server.close() }
})
