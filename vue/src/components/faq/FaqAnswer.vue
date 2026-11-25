<template>
  <article class="chat-turn" :aria-labelledby="`faq-result-${entry.id}`">
    <div class="user-bubble">
      <p class="question-label">You</p>
      <p class="question-text">{{ entry.question }}</p>
    </div>
    <div class="answer-card">
    <p class="question-label">FAQ assistant</p>
    <h3 :id="`faq-result-${entry.id}`">{{ title }}</h3>
    <template v-if="entry.result.matched">
      <p v-if="entry.result.notice" class="notice">{{ entry.result.notice }}</p>
      <p class="answer-text">{{ entry.result.answer }}</p>
      <div class="source">
        <span class="source-label">Sources</span>
        <ul>
          <li v-for="source in sources" :key="source.faqId">
            <span class="citation">[FAQ:{{ source.faqId }}]</span>
            <a v-if="source.href" :href="source.href" target="_blank" rel="noopener noreferrer">{{ source.sourceTitle }}</a>
            <template v-else>
              <span>{{ source.sourceTitle }}</span>
              <p>Source link unavailable.</p>
            </template>
          </li>
        </ul>
      </div>
    </template>
    <p v-else class="answer-text">{{ entry.result.guidance }}</p>
    </div>
  </article>
</template>

<script setup>
import { computed } from 'vue'
const props = defineProps({ entry: { type: Object, required: true } })
const title = computed(() => props.entry.result.mode === 'clarify' ? 'Please clarify' : !props.entry.result.matched ? 'No matching answer'
  : props.entry.result.mode === 'generated' ? 'AI-generated answer' : 'Stored answer')
const sources = computed(() => (props.entry.result.sources || []).map(source => ({
  ...source, href: safeUrl(source.sourceUrl),
})))
function safeUrl(value) {
  if (typeof value !== 'string' || !/^https?:\/\//i.test(value) || /[\s\u0000-\u001f\u007f]/u.test(value)) return null
  try {
    const url = new URL(value)
    return ['http:', 'https:'].includes(url.protocol) && url.hostname && !url.username && !url.password ? url.href : null
  } catch { return null }
}
</script>

<style scoped>
.chat-turn { display: grid; gap: 16px; overflow-wrap: anywhere; }
.user-bubble { justify-self: end; max-width: 85%; padding: 14px 20px; border-radius: 16px 16px 3px 16px; background: #e8f0fb; }
.answer-card { max-width: 95%; padding: 22px; border: 1px solid #dfe7ef; border-radius: 3px 16px 16px; background: #fff; }
.question-label, .source-label { color: #687b91; font-size: 12px; font-weight: 600; }
.question-text { margin-top: 6px; color: #354e69; line-height: 1.7; white-space: pre-wrap; }
h3 { color: #193e61; font-size: 17px; margin: 8px 0 12px; }
.notice { padding: 10px 12px; margin-bottom: 12px; border-radius: 6px; background: #fff7e8; color: #71501c; font-size: 13px; line-height: 1.7; }
.answer-text { color: #40566e; line-height: 1.85; white-space: pre-wrap; }
.source { margin-top: 22px; padding-top: 18px; border-top: 1px solid #edf1f6; font-size: 13px; line-height: 1.7; }
.source-label { display: block; margin-bottom: 6px; }
.source ul { list-style: none; padding: 0; margin: 0; }
.source li + li { margin-top: 9px; }
.citation { color: #687b91; margin-right: 8px; }
.source a { color: #2464a8; text-underline-offset: 3px; }
.source p { color: #687b91; }
a:focus-visible { outline: 3px solid #346bb9; outline-offset: 4px; }
@media (max-width: 600px) { .answer-card { padding: 16px; max-width: 100%; } .user-bubble { max-width: 95%; } }
</style>
