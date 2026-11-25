import { ref, onScopeDispose } from 'vue'
import { chatFaq, clearFaqConversation } from '../api/faq.js'
import { clearSession } from '../utils/session.js'

const invalidQuestion = 'Please provide a question between 1 and 1000 characters.'
const unavailable = 'FAQ service is temporarily unavailable. Please try again later.'
const genericError = 'Unable to retrieve an answer. Please try again.'
const fallbackNotice = 'AI generation is unavailable. Showing the stored FAQ answer.'
const clarificationGuidance = 'Please name the campus service, department or document you mean, and ask a complete question.'
const trimQuestion = value => value.replace(/^\p{White_Space}+|\p{White_Space}+$/gu, '')
const hasText = value => typeof value === 'string' && trimQuestion(value).length > 0
const errorStatuses = { FAQ_INVALID_QUESTION: 400, FAQ_INVALID_CHAT_REQUEST: 400, FAQ_UNAUTHENTICATED: 401,
  FAQ_FORBIDDEN: 403, FAQ_CONVERSATION_NOT_FOUND: 404, FAQ_CONVERSATION_BUSY: 409, FAQ_UNAVAILABLE: 503 }
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/

// A departing page's cleanup must finish before a replacement page creates a conversation.
// ponytail: serialize mutations within this tab; cross-tab conflicts remain server-authoritative 404/409.
let mutationTail = Promise.resolve()
function mutate(operation) {
  const task = mutationTail.then(operation)
  mutationTail = task.catch(() => {})
  return task
}

function invalidResponse(response) {
  const error = new Error('Invalid FAQ response')
  error.status = errorStatuses[response?.code]
  return error
}

function readAnswer(response) {
  const data = response?.data
  if (response?.code !== '0' || typeof data?.matched !== 'boolean') {
    throw invalidResponse(response)
  }
  if (data.mode === 'no_match' && !data.matched) {
    return { matched: false, mode: 'no_match', sources: [], notice: null,
      guidance: 'No matching answer was found. Please contact student support.' }
  }
  if (data.mode === 'clarify' && !data.matched && data.answer === null && data.notice === null
    && Array.isArray(data.sources) && data.sources.length === 0) {
    return { matched: false, mode: 'clarify', sources: [], notice: null, guidance: clarificationGuidance }
  }
  if (!data.matched || !['generated', 'extractive'].includes(data.mode) || !hasText(data.answer)
    || !Array.isArray(data.sources) || !data.sources.length || data.sources.length > 3
    || (data.mode === 'extractive' && (!hasText(data.notice) || data.sources.length !== 1))
    || (data.mode === 'generated' && data.notice !== null)) throw new Error('Invalid FAQ answer')
  const ids = new Set()
  const sources = data.sources.map(source => {
    if (!Number.isSafeInteger(source?.faqId) || source.faqId <= 0 || ids.has(source.faqId)
      || !hasText(source.sourceTitle)) throw new Error('Invalid FAQ source')
    ids.add(source.faqId)
    return { faqId: source.faqId, sourceTitle: source.sourceTitle,
      sourceUrl: typeof source.sourceUrl === 'string' ? source.sourceUrl : null }
  })
  // Keep only public display fields; never surface upstream diagnostics or entities.
  return { matched: true, mode: data.mode, answer: data.answer, sources,
    notice: data.mode === 'extractive' ? fallbackNotice : null }
}

export function useFaq() {
  const question = ref('')
  const loading = ref(false)
  const error = ref('')
  const needsSignIn = ref(false)
  const history = ref([])
  const pendingQuestion = ref('')
  const status = ref('')
  const conversationId = ref(null)
  const processing = ref(false)
  const resetting = ref(false)
  const needsNewConversation = ref(false)
  let active
  let disposed = false
  let sequence = 0
  let touched = false
  let resetTask

  function showFailure(failure, { reset = false, malformed = false } = {}) {
    const code = failure.response?.status ?? failure.status
    if (code === 401) {
      needsSignIn.value = true
      history.value = []
      conversationId.value = null
      needsNewConversation.value = false
      try { clearSession(globalThis.localStorage) } catch { /* Session remains authoritative. */ }
      error.value = 'Your session has expired. Please sign in again.'
    } else if (reset) {
      needsNewConversation.value = true
      error.value = code === 403 ? 'You do not have permission to clear this conversation.'
        : 'Unable to clear the conversation. Please retry New conversation before sending.'
    } else if (code === 404) {
      needsNewConversation.value = true
      error.value = 'This conversation is no longer available. Please start a new conversation.'
    } else if (code === 409) {
      error.value = 'This conversation is still processing a question. Retry later or start a new conversation.'
    } else if (code === 400) error.value = invalidQuestion
    else if (code === 403) error.value = 'You do not have permission to use FAQ assistance.'
    else if (code === 503) error.value = unavailable
    else if (['ECONNABORTED', 'ETIMEDOUT'].includes(failure.code)) {
      error.value = 'The request timed out. Please try again.'
    } else if (failure.code === 'ERR_NETWORK') {
      error.value = 'Unable to connect to FAQ assistance. Check your connection and try again.'
    } else {
      error.value = genericError
      if (malformed) {
        needsNewConversation.value = true
        error.value += ' Please start a new conversation.'
      }
    }
  }

  async function submit() {
    if (disposed || processing.value || resetting.value || needsNewConversation.value || needsSignIn.value) return
    const submitted = trimQuestion(question.value)
    error.value = ''
    if (!submitted || submitted.length > 1000) {
      error.value = invalidQuestion
      return
    }
    loading.value = true
    processing.value = true
    status.value = ''
    pendingQuestion.value = submitted
    const current = { stopped: false, id: null }
    active = current
    let received = false
    try {
      const response = await mutate(() => {
        if (disposed || active !== current) return null
        touched = true
        current.id = conversationId.value
        // Retain transport after Stop so a late create cannot arrive after its cleanup.
        return chatFaq(submitted, current.id)
      })
      if (disposed || active !== current) return
      received = true
      const result = readAnswer(response)
      const id = response.data.conversationId
      if (typeof id !== 'string' || !uuid.test(id) || (current.id !== null && id !== current.id)) {
        throw invalidResponse(response)
      }
      conversationId.value = id
      if (current.stopped) status.value = 'The server finished processing. You can send another question or start a new conversation.'
      else {
        history.value = [...history.value, { id: ++sequence, question: submitted, result }].slice(-5)
        question.value = ''
      }
    } catch (failure) {
      if (disposed || active !== current) return
      status.value = ''
      showFailure(failure, { malformed: received })
    } finally {
      if (active === current) {
        active = null
        pendingQuestion.value = ''
        loading.value = false
        processing.value = false
      }
    }
  }

  function stop() {
    if (!active || active.stopped) return
    active.stopped = true
    loading.value = false
    pendingQuestion.value = ''
    status.value = 'Stopped waiting. The server may still be processing. Wait for it to finish or start a new conversation.'
  }

  function newConversation() {
    if (disposed || needsSignIn.value) return Promise.resolve(false)
    if (resetTask) return resetTask
    active = null // Old success, failure and finally cannot write into this reset.
    loading.value = false
    processing.value = false
    pendingQuestion.value = ''
    resetting.value = true
    status.value = 'Starting a new conversation…'
    error.value = ''
    resetTask = mutate(() => { touched = true; return clearFaqConversation() })
      .then(response => {
        if (disposed) return false
        if (response?.code !== '0' || response.data !== null) throw invalidResponse(response)
        conversationId.value = null
        history.value = []
        question.value = ''
        needsNewConversation.value = false
        status.value = 'New conversation ready.'
        return true
      }).catch(failure => {
        if (!disposed) { status.value = ''; showFailure(failure, { reset: true }) }
        return false
      }).finally(() => { if (!disposed) resetting.value = false; resetTask = null })
    return resetTask
  }

  onScopeDispose(() => {
    const shouldClear = touched && !needsSignIn.value && !resetTask
    disposed = true
    active = null
    conversationId.value = null
    history.value = []
    question.value = ''
    pendingQuestion.value = ''
    status.value = ''
    error.value = ''
    loading.value = false
    processing.value = false
    resetting.value = false
    if (shouldClear) mutate(() => clearFaqConversation()).catch(() => {})
  })

  return { question, loading, error, needsSignIn, history, pendingQuestion, status,
    conversationId, processing, resetting, needsNewConversation,
    submit, stop, newConversation }
}
