<template>
  <section class="faq-page" aria-labelledby="faq-title">
    <RouterLink to="/home" class="back-link"><span aria-hidden="true">←</span> Back to home</RouterLink>
    <header class="page-heading">
      <div><p class="eyebrow">{{ module.group }} / {{ module.id }}</p><h1 id="faq-title">{{ module.name }}</h1></div>
      <span class="status">{{ module.status }}</span>
    </header>
    <div class="preview-note">
      <strong>Campus answers with official sources</strong>
      <p>Follow-up questions can use the recent conversation to identify the topic. Answers use the reviewed knowledge base and link to official sources. AI-generated answers are labelled. Stored answers may be shown when available; if evidence is insufficient, the assistant will not answer.</p>
    </div>
    <div class="chat-panel">
      <div class="conversation-heading">
        <h2 id="conversation-title">Conversation</h2>
        <button type="button" class="text-button" :disabled="resetting || needsSignIn" @click="startNew">{{ resetting ? 'Starting…' : 'New conversation' }}</button>
      </div>
      <p id="history-help" class="help-text">The five most recent completed exchanges appear here. New conversation clears this session's conversation after confirmation. Leaving clears this page and requests conversation cleanup.</p>
      <div ref="conversation" class="conversation" role="region" aria-labelledby="conversation-title" aria-describedby="history-help" tabindex="0">
        <p v-if="!history.length && !loading" class="empty-state">Ask a question about CCNU campus services. Your answers and sources will appear here.</p>
        <div class="answer-list" aria-live="polite" aria-relevant="additions">
          <FaqAnswer v-for="entry in history" :key="entry.id" :entry="entry" />
          <div v-if="loading" class="pending-turn">
            <div class="pending-question"><strong>You</strong><p>{{ pendingQuestion }}</p></div>
            <p class="loading-message" role="status">FAQ assistant is preparing an answer…</p>
          </div>
        </div>
      </div>
      <div v-if="!history.length && !loading" class="examples" aria-label="Example questions">
        <span>Try a question</span>
        <button v-for="example in examples" :key="example.label" type="button" :disabled="needsSignIn || processing || resetting || needsNewConversation" @click="chooseExample(example.question)">{{ example.label }}</button>
      </div>
      <form class="question-panel" :aria-busy="loading || resetting" novalidate @submit.prevent="submit">
        <h2>Ask a question</h2>
        <p id="question-help" class="help-text">Ask in English. Use up to 1,000 characters. Ctrl+Enter sends; Enter adds a line.</p>
        <label for="faq-question">Your question</label>
        <textarea id="faq-question" ref="input" v-model="question" rows="3" maxlength="1000" :disabled="loading || resetting || needsSignIn"
                  aria-describedby="question-help question-count faq-error" @keydown.ctrl.enter.prevent="!$event.isComposing && submit()"
                  placeholder="For example, where can I find the academic calendar?" />
        <div class="form-actions">
          <span id="question-count" class="character-count">{{ question.length }} / 1,000</span>
          <button v-if="loading" type="button" class="stop-button" @click="stop">Stop</button>
          <button v-else type="submit" class="submit-button" :disabled="needsSignIn || processing || resetting || needsNewConversation">Send</button>
        </div>
        <p v-if="status" class="help-text" role="status">{{ status }}</p>
        <div id="faq-error" :role="error ? 'alert' : undefined" class="error-message">
          <p v-if="error">{{ error }}</p>
          <RouterLink v-if="needsSignIn" :to="{ name: 'Login', query: { reason: 'expired', redirect: '/faq' } }">Sign in again</RouterLink>
        </div>
      </form>
    </div>
  </section>
</template>

<script setup>
import { ref, watch, nextTick } from 'vue'
import { RouterLink } from 'vue-router'
import FaqAnswer from '../../components/faq/FaqAnswer.vue'
import { useFaq } from '../../composables/useFaq.js'
defineProps({ module: { type: Object, required: true } })
const { question, loading, error, needsSignIn, history, pendingQuestion, status,
  processing, resetting, needsNewConversation, submit, stop, newConversation } = useFaq()
const conversation = ref(null)
const input = ref(null)
const examples = [
  { label: 'Academic calendar', question: 'Where can I find the CCNU academic calendar for 2026-2027?' },
  { label: 'Course-selection enquiries', question: 'Who can I contact about undergraduate course selection?' },
]
function chooseExample(value) { question.value = value; input.value?.focus() }
async function startNew() { if (await newConversation()) { await nextTick(); input.value?.focus() } }
watch([history, loading, resetting], async () => {
  await nextTick()
  if (conversation.value) conversation.value.scrollTop = conversation.value.scrollHeight
  if (!loading.value && !resetting.value && !needsSignIn.value) input.value?.focus()
})
</script>

<style scoped>
.faq-page { max-width: 960px; margin: 0 auto; padding: 32px; }
.back-link { display: inline-flex; gap: 8px; color: #526c88; font-size: 13px; text-decoration: none; }
.back-link:hover { text-decoration: underline; }
.page-heading { display: flex; justify-content: space-between; align-items: center; gap: 16px; margin: 24px 0 20px; }
.eyebrow { margin-bottom: 8px; color: #6c7c8f; font-size: 12px; }
h1 { font-size: clamp(25px, 3vw, 34px); color: #193e61; letter-spacing: -.6px; line-height: 1.3; }
h2 { font-size: 17px; color: #193e61; line-height: 1.4; }
.status { flex-shrink: 0; padding: 7px 11px; background: #fbf4e6; color: #806025; border-radius: 20px; font-size: 11px; }
.preview-note { padding: 15px 20px; border: 1px solid #cee3f4; border-radius: 12px; background: #edf6ff; color: #31597e; font-size: 13px; line-height: 1.7; }
.preview-note p { margin-top: 4px; }
.chat-panel { margin-top: 22px; padding: 22px; border: 1px solid #dfe7ef; border-radius: 14px; background: #fff; }
.conversation-heading { display: flex; justify-content: space-between; align-items: center; gap: 12px; }
.text-button { padding: 6px 0; border: 0; background: none; color: #31597e; text-decoration: underline; text-underline-offset: 3px; }
.help-text { margin: 8px 0 14px; color: #65758a; font-size: 12px; line-height: 1.7; }
.conversation { max-height: 55vh; min-height: 180px; overflow-y: auto; overscroll-behavior: contain; padding: 12px 4px; }
.empty-state { padding: 40px 16px; color: #687b91; font-size: 14px; text-align: center; line-height: 1.8; }
.answer-list { display: grid; gap: 28px; }
.pending-turn { display: grid; gap: 12px; overflow-wrap: anywhere; }
.pending-question { justify-self: end; max-width: 85%; padding: 14px 20px; border-radius: 16px 16px 3px 16px; background: #e8f0fb; color: #354e69; }
.pending-question strong { font-size: 12px; }
.pending-question p { margin-top: 6px; white-space: pre-wrap; line-height: 1.7; }
.loading-message { color: #31597e; font-size: 13px; padding: 12px; }
.examples { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; margin: 12px 0; color: #687b91; font-size: 12px; }
.examples button { padding: 8px 10px; border: 1px solid #d4e0ed; border-radius: 8px; background: #f8fbff; color: #31597e; font-size: 12px; text-align: left; }
.question-panel { margin-top: 16px; padding-top: 18px; border-top: 1px solid #e7edf4; }
label { display: block; margin-bottom: 8px; color: #354e69; font-size: 13px; font-weight: 600; }
textarea { display: block; width: 100%; resize: vertical; min-height: 90px; padding: 12px; border: 1px solid #bdccdd; border-radius: 9px; font: inherit; font-size: 14px; color: #253f5a; line-height: 1.7; background: #fcfdff; }
textarea::placeholder { color: #7a8da0; }
.form-actions { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-top: 12px; }
.character-count { color: #6c7c8f; font-size: 12px; }
button { font: inherit; cursor: pointer; }
.submit-button, .stop-button { padding: 10px 24px; border: 0; border-radius: 8px; background: #2e68b3; color: #fff; font-size: 14px; font-weight: 600; }
.stop-button { background: #854329; }
.submit-button:hover:not(:disabled) { background: #23558e; }
button:disabled, textarea:disabled { opacity: .6; cursor: not-allowed; }
.error-message { color: #a62c2c; font-size: 14px; line-height: 1.7; }
.error-message:not(:empty) { margin-top: 14px; }
.error-message a { color: #235b99; display: inline-block; margin-top: 7px; }
a:focus-visible, button:focus-visible, textarea:focus-visible, .conversation:focus-visible { outline: 3px solid #346bb9; outline-offset: 3px; }
@media (max-width: 600px) {
  .faq-page { padding: 24px 14px; }
  .page-heading { align-items: flex-start; flex-direction: column; gap: 10px; }
  .chat-panel { padding: 14px; }
  .preview-note { padding: 14px; }
  .conversation { max-height: 50vh; }
  .pending-question { max-width: 95%; }
}
</style>
