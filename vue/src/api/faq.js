import request from '../utils/request.js'

export function askFaq(question, { signal } = {}) {
  return request.post('/faq/ask', { question }, { signal, timeout: 45000 })
}

export function chatFaq(question, conversationId, { signal } = {}) {
  return request.post('/faq/chat', { question, conversationId }, { signal, timeout: 45000 })
}

export function clearFaqConversation() {
  return request.delete('/faq/chat/current', { timeout: 45000 })
}
