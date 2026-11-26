package com.example.springboot.faq;

import com.example.springboot.faq.config.FaqLlmProperties;
import com.example.springboot.faq.controller.FaqChatController;
import com.example.springboot.faq.controller.FaqController;
import com.example.springboot.faq.entity.FaqEntry;
import com.example.springboot.faq.llm.LlmClient;
import com.example.springboot.faq.mapper.FaqMapper;
import com.example.springboot.faq.retrieval.TfIdfRetriever;
import com.example.springboot.faq.service.FaqConversationService;
import com.example.springboot.faq.service.FaqRequestRunner;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.*;
import java.util.concurrent.*;
import javax.servlet.http.HttpSessionBindingEvent;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FaqChatControllerTest {
    private final ObjectMapper json = new ObjectMapper();
    private final ExecutorService callers = Executors.newFixedThreadPool(2);
    private FaqMapper mapper;
    private FaqRequestRunner runner;
    private FaqConversationService conversations;
    private MockMvc mvc;
    private FaqEntry entry;
    private CountDownLatch release;

    @BeforeEach void setup() {
        entry = new FaqEntry();
        entry.setId(7L);
        entry.setQuestion("Password reset");
        entry.setAnswer("Stored fixture answer.");
        entry.setSourceTitle("Fixture official source");
        entry.setSourceUrl("https://jwc.ccnu.edu.cn/ywgl/lxwm.htm");
        entry.setEnabled(true);
        mapper = mock(FaqMapper.class);
        when(mapper.findEnabled()).thenReturn(Collections.singletonList(entry));
        LlmClient llm = new LlmClient(new FaqLlmProperties(false, "", "", "", "max_completion_tokens"),
                json, new RestTemplateBuilder());
        runner = new FaqRequestRunner(new com.example.springboot.faq.service.FaqService(
                mapper, new TfIdfRetriever(), 0.35, llm), 35000);
        conversations = new FaqConversationService(runner, 35000);
        mvc = MockMvcBuilders.standaloneSetup(new FaqChatController(conversations, json),
                new FaqController(runner, json)).build();
    }

    @AfterEach void cleanup() {
        if (release != null) release.countDown();
        runner.close();
        callers.shutdownNow();
    }

    @ParameterizedTest @ValueSource(strings = {"stu", "dormManager", "admin"})
    void permittedRolesCreateContinueAndKeepAskCompatible(String role) throws Exception {
        MockHttpSession session = session(role);
        MvcResult first = ask(session, "Password reset", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.msg").value("Success"))
                .andExpect(jsonPath("$.data.mode").value("extractive"))
                .andExpect(jsonPath("$.data.answer").value("Stored fixture answer."))
                .andExpect(jsonPath("$.data.sources[0].faqId").value(7))
                .andExpect(jsonPath("$.data.history").doesNotExist()).andReturn();
        String id = id(first);
        assertEquals(id, UUID.fromString(id).toString());
        ask(session, "Password reset", id).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conversationId").value(id));
        mvc.perform(post("/faq/ask").session(session).contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"Password reset\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.conversationId").doesNotExist());
    }

    @Test void authenticationAndRoleChecksApplyToBothEndpoints() throws Exception {
        for (String role : new String[]{null, "guest"}) {
            MockHttpSession session = role == null ? new MockHttpSession() : session(role);
            int expected = role == null ? 401 : 403;
            ask(session, "Password reset", null).andExpect(status().is(expected));
            mvc.perform(delete("/faq/chat/current").session(session)).andExpect(status().is(expected));
        }
        MockHttpSession incomplete = new MockHttpSession();
        incomplete.setAttribute("Identity", "stu");
        ask(incomplete, "Password reset", null).andExpect(status().isUnauthorized());
        verifyNoInteractions(mapper);
    }

    @ParameterizedTest @ValueSource(strings = {"", "null", "[]", "{}", "true", "{broken",
            "{\"question\":null}", "{\"question\":123}", "{\"question\":true}",
            "{\"question\":[]}", "{\"question\":\" \\u00a0 \"}",
            "{\"question\":\"Password reset\",\"conversationId\":123}",
            "{\"question\":\"Password reset\",\"conversationId\":\"\"}",
            "{\"question\":\"Password reset\",\"conversationId\":\"1-1-1-1-1\"}",
            "{\"question\":\"Password reset\",\"history\":[]}",
            "{\"question\":\"Password reset\",\"role\":\"admin\"}",
            "{\"question\":\"Password reset\",\"question\":\"another\"}",
            "{\"question\":\"Password reset\"} {}", "{\"question\":\"Password reset\"} trailing"})
    void invalidRequestsNeverCreateConversationOrQueryKnowledge(String body) throws Exception {
        MockHttpSession session = session("stu");
        mvc.perform(post("/faq/chat").session(session).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FAQ_INVALID_CHAT_REQUEST"));
        assertNull(session.getAttribute("faq.conversation"));
        verifyNoInteractions(mapper);
    }

    @Test void unicodeTrimLengthAndUppercaseUuidAreHandled() throws Exception {
        MockHttpSession session = session("stu");
        String id = id(ask(session, "\u00a0 Password reset \u2003", null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.matched").value(true)).andReturn());
        ask(session, "Password reset", id.toUpperCase(Locale.ROOT)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conversationId").value(id));
        String thousand = String.join("", Collections.nCopies(1000, "x"));
        ask(session, " " + thousand + " ", id).andExpect(status().isOk());
        ask(session, thousand + "x", id).andExpect(status().isBadRequest());
    }

    @Test void foreignUnknownAndReplacedIdsAreIndistinguishable() throws Exception {
        MockHttpSession first = session("stu"), second = session("stu");
        String old = id(ask(first, "Password reset", null).andReturn());
        for (String unavailable : new String[]{old, UUID.randomUUID().toString()}) {
            ask(second, "Password reset", unavailable).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("FAQ_CONVERSATION_NOT_FOUND"));
        }
        String replacement = id(ask(first, "Password reset", null).andReturn());
        assertNotEquals(old, replacement);
        ask(first, "Password reset", old).andExpect(status().isNotFound());
        ask(first, "Password reset", replacement).andExpect(status().isOk());
    }

    @Test void clearIsIdempotentAndInvalidatesContinuation() throws Exception {
        MockHttpSession session = session("stu");
        String id = id(ask(session, "Password reset", null).andReturn());
        for (int i = 0; i < 2; i++) mvc.perform(delete("/faq/chat/current").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.msg").value("Success"))
                .andExpect(jsonPath("$.data").value(nullValue()));
        assertNull(session.getAttribute("faq.conversation"));
        ask(session, "Password reset", id).andExpect(status().isNotFound());
    }

    @Test void failedLookupDoesNotAppendAndBusyFlagIsReleased() throws Exception {
        MockHttpSession session = session("stu");
        String id = id(ask(session, "Password reset", null).andReturn());
        when(mapper.findEnabled()).thenThrow(new DataAccessResourceFailureException("private SQL and key"));
        ask(session, "Password reset", id).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("FAQ_UNAVAILABLE"))
                .andExpect(content().string(not(containsString("private SQL"))));
        assertEquals(1, history(session).size());
        doReturn(Collections.singletonList(entry)).when(mapper).findEnabled();
        ask(session, "Password reset", id).andExpect(status().isOk());
        assertEquals(2, history(session).size());
    }

    @Test void historyEvictsOldPairsTruncatesWithoutSplittingSurrogatesAndPreservesHttpAnswer() throws Exception {
        MockHttpSession session = session("stu");
        String longAnswer = String.join("", Collections.nCopies(1999, "a")) + "\ud83d\ude00tail";
        entry.setAnswer(longAnswer);
        String id = null;
        for (int i = 0; i < 5; i++) {
            MvcResult result = ask(session, "Password reset " + i, id).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.answer").value(longAnswer)).andReturn();
            id = id(result);
        }
        List<?> turns = new ArrayList<>(history(session));
        assertEquals(3, turns.size());
        assertEquals("Password reset 2", ReflectionTestUtils.getField(turns.get(0), "question"));
        for (Object turn : turns) {
            String answer = (String) ReflectionTestUtils.getField(turn, "answer");
            assertEquals(1999, answer.length());
            assertFalse(Character.isHighSurrogate(answer.charAt(answer.length() - 1)));
        }
        ask(session, "volcano eruption", id).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("no_match"))
                .andExpect(jsonPath("$.data.sources", hasSize(0)));
        Object last = new ArrayList<>(history(session)).get(2);
        assertEquals("no_match", ReflectionTestUtils.getField(last, "mode"));
    }

    @Test void duplicateContinuationIsBusyWhileAnotherSessionStillWorks() throws Exception {
        MockHttpSession session = session("stu");
        String id = id(ask(session, "Password reset", null).andReturn());
        CountDownLatch entered = blockNextLookup();
        Future<MvcResult> waiting = callers.submit(() -> ask(session, "Password reset", id).andReturn());
        assertTrue(entered.await(2, TimeUnit.SECONDS));
        ask(session, "Password reset", id).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FAQ_CONVERSATION_BUSY"));
        ask(session("admin"), "Password reset", null).andExpect(status().isOk());
        release.countDown();
        assertEquals(200, waiting.get(2, TimeUnit.SECONDS).getResponse().getStatus());
    }

    @ParameterizedTest @ValueSource(strings = {"clear", "replace", "expire", "signout"})
    void cancellingLifecycleRejectsOldCompletionAndCannotRestoreOldHistory(String action) throws Exception {
        MockHttpSession session = session("stu");
        String id = id(ask(session, "Password reset", null).andReturn());
        CountDownLatch entered = blockNextLookup();
        Future<MvcResult> waiting = callers.submit(() -> ask(session, "Password reset", id).andReturn());
        assertTrue(entered.await(2, TimeUnit.SECONDS));
        String replacement = null;
        if (action.equals("clear")) mvc.perform(delete("/faq/chat/current").session(session)).andExpect(status().isOk());
        if (action.equals("replace")) replacement = id(ask(session, "Password reset", null).andExpect(status().isOk()).andReturn());
        if (action.equals("expire")) session.invalidate();
        if (action.equals("signout")) {
            Object user = session.getAttribute("User");
            session.removeAttribute("User");
            conversations.sessionCleanupListener().getListener().attributeRemoved(new HttpSessionBindingEvent(session, "User", user));
        }
        // Must finish without waiting for the blocked lookup to be released.
        MvcResult old = waiting.get(2, TimeUnit.SECONDS);
        assertEquals(action.equals("expire") || action.equals("signout") ? 401 : 404,
                old.getResponse().getStatus());
        release.countDown();
        if (replacement != null) {
            ask(session, "Password reset", replacement).andExpect(status().isOk());
            assertEquals(2, history(session).size());
        } else if (!action.equals("expire")) assertNull(session.getAttribute("faq.conversation"));
    }

    @Test void reloginInSameSessionCannotReusePreviousUsersConversation() throws Exception {
        MockHttpSession session = session("stu");
        String id = id(ask(session, "Password reset", null).andReturn());
        session.setAttribute("User", new Object());
        ask(session, "Password reset", id).andExpect(status().isNotFound());
        assertNull(session.getAttribute("faq.conversation"));
    }

    @ParameterizedTest @ValueSource(strings = {"chat", "clear"})
    void expirationBetweenAuthorizationAndSessionMutationStillReturnsUnauthorized(String operation) {
        MockHttpSession raced = spy(session("stu"));
        doAnswer(invocation -> {
            Object user = invocation.callRealMethod();
            raced.invalidate();
            return user;
        }).when(raced).getAttribute("User");
        FaqConversationService.ConversationException error = assertThrows(
                FaqConversationService.ConversationException.class,
                () -> { if (operation.equals("chat")) conversations.chat("Password reset", null, raced);
                    else conversations.clear(raced); });
        assertEquals(401, error.getStatus());
        assertEquals("FAQ_UNAUTHENTICATED", error.getCode());
        verifyNoInteractions(mapper);
    }

    private CountDownLatch blockNextLookup() {
        CountDownLatch entered = new CountDownLatch(1);
        release = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicBoolean first = new java.util.concurrent.atomic.AtomicBoolean(true);
        when(mapper.findEnabled()).thenAnswer(invocation -> {
            if (first.getAndSet(false)) {
                entered.countDown();
                // Simulate JDBC that does not stop immediately when interrupted.
                boolean interrupted = false;
                while (true) {
                    try { if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("Lookup not released"); break; }
                    catch (InterruptedException e) { interrupted = true; }
                }
                if (interrupted) Thread.currentThread().interrupt();
            }
            return Collections.singletonList(entry);
        });
        return entered;
    }

    private org.springframework.test.web.servlet.ResultActions ask(MockHttpSession session, String question, String id) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("question", question); body.put("conversationId", id);
        return mvc.perform(post("/faq/chat").session(session).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }
    private String id(MvcResult result) throws Exception {
        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        return body.path("data").path("conversationId").asText();
    }
    private Collection<?> history(MockHttpSession session) {
        return (Collection<?>) ReflectionTestUtils.getField(session.getAttribute("faq.conversation"), "history");
    }
    private MockHttpSession session(String role) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("User", new Object()); session.setAttribute("Identity", role);
        return session;
    }
}
