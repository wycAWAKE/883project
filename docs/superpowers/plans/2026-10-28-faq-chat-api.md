# FAQ Chat API Implementation Plan

> **For agentic workers:** Use executing-plans inline and test-driven-development. Preserve the existing uncommitted workspace; do not commit Git changes.

**Goal:** Implement the C5 HTTP/session foundation requested by “现在增加聊天接口”.

**Architecture:** A FAQ-only controller validates a strict JSON request and delegates to a Session-local conversation service. Reuse FaqRequestRunner and the existing single-question RAG pipeline. Store at most three bounded completed exchanges; history is not yet used for rewriting or generation.

**Tech Stack:** Existing Java 8 source target, Spring Boot 2.6.3, Jackson, Servlet Session and JUnit/MockMvc; no new dependencies.

**Spec:** springboot/src/main/java/com/example/springboot/faq/FAQ_CHAT_PLAN.md, C1 conversation contract. This task implements its interface/session portion, not all C5.

## Global Constraints

- Preserve /faq/ask, Session User/Identity and string success code "0"; product messages English.
- POST /faq/chat accepts only question and optional conversationId; DELETE /faq/chat/current clears the current conversation.
- Question Unicode-trimmed 1–1000 UTF-16 units; conversationId must be a canonical UUID. Reject scalar coercion, unknown/duplicate fields and trailing JSON.
- Each Session has one conversation. Unknown/foreign IDs → 404 FAQ_CONVERSATION_NOT_FOUND; simultaneous continuation → 409 FAQ_CONVERSATION_BUSY.
- Keep three complete pairs, each question <=1000, answer/guidance <=2000 UTF-16 units, no split surrogate pairs; failures do not append.
- New/cleared/expired/signed-out conversations cancel old waiting and reject stale completion. Cancellation does not guarantee upstream termination or stopped billing.
- Reuse the 35-second request budget and four-worker/no-queue executor. Models stay disabled by default; no real model calls, SQL, frontend/config/dependency/global-auth changes.

## Review Focus

- Late completions after replacement/clear must not modify the current Session state.
- Login changes within the same Session must not expose prior-user history.
- Concurrent continuations return 409 promptly rather than queueing under a long-held Session lock.
- Failed retrieval must release busy state and leave history unchanged.
- Input type coercion and history truncation must not weaken trust/memory boundaries.

### Task 1: HTTP and Session lifecycle

**Files:** Add faq/controller/FaqChatController.java, dto/FaqChatRequest.java, dto/FaqChatResponse.java, service/FaqConversationService.java and test/faq/FaqChatControllerTest.java. Modify FaqRequestRunner/FaqRequestBudget only to support cancellation by the owning conversation. Update existing FAQ development docs.

**Interfaces:** POST /faq/chat → existing answer fields plus conversationId; DELETE /faq/chat/current → {code:"0",msg:"Success",data:null}. FaqConversationService.chat(question,conversationId,session) → FaqChatResponse; clear(session) clears and cancels.

- [x] Write MockMvc tests for roles/auth, strict inputs, reuse/replacement/isolation, three modes, busy/clear/replace/expiry races and bounded history.
- [x] Run mvn -o -Dtest=FaqChatControllerTest test; observe failure because chat API is absent.
- [x] Implement the DTOs/controller/session service and cancellation wiring, using Session-local short critical sections and no locks across RAG work.
- [x] Run targeted tests, full mvn -o test and mvn -o -DskipTests compile; require zero failures/errors.
- [x] Perform one independent review, fix significant findings with RED→GREEN tests, update actual status and verification evidence in FAQ docs.

Execution ruling: user has requested implementation of the already described interface; execute directly without an additional plan-approval round. Existing FAQ code is untracked, so a fresh worktree would omit the required baseline; work in place, preserve modifications, and retain evidence under ignored target/faq-c5-api.
