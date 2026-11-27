package com.example.springboot.faq;

import com.example.springboot.faq.config.FaqLlmProperties;
import com.example.springboot.faq.controller.FaqChatController;
import com.example.springboot.faq.dto.FaqAnswerResponse;
import com.example.springboot.faq.entity.FaqEntry;
import com.example.springboot.faq.llm.LlmClient;
import com.example.springboot.faq.mapper.FaqMapper;
import com.example.springboot.faq.retrieval.TfIdfRetriever;
import com.example.springboot.faq.service.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real chat state, bounded runner, RAG and client; DB and provider are the only substitutes. */
class FaqMultiTurnTest {
    private final ObjectMapper json = new ObjectMapper();
    private FaqMapper mapper;
    private FaqRequestRunner runner;
    private MockRestServiceServer upstream;
    private MockMvc mvc;
    private MockHttpSession session;

    @BeforeEach void setup() { configure(true, 35000); }
    private void configure(boolean enabled, long timeout) {
        if (runner != null) runner.close();
        mapper = mock(FaqMapper.class);
        when(mapper.findEnabled()).thenReturn(Arrays.asList(entry(4, "Undergraduate course selection contact"),
                entry(1, "Academic calendar 2026 2027")));
        LlmClient client = new LlmClient(new FaqLlmProperties(enabled, "https://fixture.example/v1",
                "fixture-key", "deepseek-flash", "max_tokens"), json,
                new RestTemplateBuilder().additionalCustomizers(template ->
                        upstream = MockRestServiceServer.bindTo(template).build()));
        runner = new FaqRequestRunner(new FaqService(mapper, new TfIdfRetriever(), 0.35, client), timeout);
        mvc = MockMvcBuilders.standaloneSetup(new FaqChatController(new FaqConversationService(runner, timeout), json)).build();
        session = new MockHttpSession();
        session.setAttribute("User", "PRIVATE_ACCOUNT"); session.setAttribute("Identity", "stu");
    }
    @AfterEach void close() { runner.close(); }

    @Test void chainedFollowUpsKeepResolvedTopicAfterOriginalTurnIsEvictedAndRegenerateFromFreshEvidence() throws Exception {
        generation("Undergraduate course selection contact", 4, "Old answer [FAQ:4]");
        for (int i = 0; i < 4; i++) {
            rewrite("follow_up", "Undergraduate course selection contact", "Undergraduate course selection contact");
            generation("Undergraduate course selection contact", 4, "Current answer [FAQ:4]");
        }
        String id = chat("Undergraduate course selection contact", null).path("conversationId").asText();
        for (int i = 0; i < 4; i++) assertEquals("generated", chat("What is its phone number?", id).path("mode").asText());
        verify(mapper, times(5)).findEnabled();
        upstream.verify();
    }

    @Test void standaloneTopicSwitchSkipsRewriteAndDiscardsEarlierTopic() throws Exception {
        generation("Undergraduate course selection contact", 4, "Contact [FAQ:4]");
        generation("Academic calendar 2026 2027", 1, "Calendar [FAQ:1]");
        rewrite("follow_up", "Academic calendar 2026 2027", "Academic calendar 2026 2027");
        generation("Academic calendar 2026 2027", 1, "Calendar again [FAQ:1]");
        String id = chat("Undergraduate course selection contact", null).path("conversationId").asText();
        chat("Academic calendar 2026 2027", id);
        assertEquals(1, chat("Where can I find it?", id).path("faqId").asInt());
        upstream.verify();
    }

    @ParameterizedTest @ValueSource(strings = {"What is its phone number?", "And the deadline?", "Tell me more", "What is the phone number?"})
    void referenceWithoutHistoryClarifiesWithoutKnowledgeOrModelCall(String question) throws Exception {
        JsonNode data = chat(question, null);
        assertEquals("clarify", data.path("mode").asText());
        assertFalse(data.path("matched").asBoolean());
        assertTrue(data.path("answer").isNull());
        assertEquals(0, data.path("sources").size());
        assertTrue(data.path("guidance").asText().contains("Please name"));
        verifyNoInteractions(mapper); upstream.verify();
    }

    @ParameterizedTest @ValueSource(strings = {"ambiguous", "invalid", "rate_limit", "no_match", "deleted", "disabled"})
    void ambiguityFailureOrAbsentEvidenceNeverUsesHistoricalAnswerAsEvidence(String scenario) throws Exception {
        generation("Undergraduate course selection contact", 4, "Old phone 999999 [FAQ:4]");
        if ("ambiguous".equals(scenario)) rewrite("clarify", "", "Undergraduate course selection contact");
        else if ("invalid".equals(scenario)) upstream.expect(anything()).andRespond(withSuccess(envelope("{\"action\":\"follow_up\",\"question\":\"https://evil.example\"}"), MediaType.APPLICATION_JSON));
        else if ("rate_limit".equals(scenario)) upstream.expect(anything()).andRespond(withStatus(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS));
        else if (!"disabled".equals(scenario)) rewrite("follow_up", "no_match".equals(scenario) ? "volcano eruption" : "Undergraduate course selection contact", "Undergraduate course selection contact");
        String id = chat("Undergraduate course selection contact", null).path("conversationId").asText();
        if ("deleted".equals(scenario)) when(mapper.findEnabled()).thenReturn(Collections.emptyList());
        if ("disabled".equals(scenario)) {
            // A fresh disabled client is exercised below; configuration is immutable in a running app.
            upstream.verify(); configure(false, 35000);
            id = chat("Undergraduate course selection contact", null).path("conversationId").asText();
        }
        JsonNode data = chat("What is its phone number?", id);
        assertEquals(Arrays.asList("no_match", "deleted").contains(scenario) ? "no_match" : "clarify", data.path("mode").asText());
        assertFalse(data.toString().contains("999999"));
        upstream.verify();
    }

    @Test void explicitNewTopicFromResolverDoesNotPolluteLaterReference() throws Exception {
        generation("Undergraduate course selection contact", 4, "Contact [FAQ:4]");
        rewrite("new_topic", "Academic calendar 2026 2027", "Undergraduate course selection contact");
        generation("Academic calendar 2026 2027", 1, "Calendar [FAQ:1]");
        rewrite("follow_up", "Academic calendar 2026 2027", "Academic calendar 2026 2027");
        generation("Academic calendar 2026 2027", 1, "Calendar [FAQ:1]");
        String id = chat("Undergraduate course selection contact", null).path("conversationId").asText();
        chat("What about the academic calendar?", id);
        chat("Where is it?", id);
        upstream.verify();
    }

    @Test void unmatchedHistoryIsNotAnEstablishedTopicAndCannotTriggerARewrite() throws Exception {
        String id = chat("volcano eruption", null).path("conversationId").asText();
        assertEquals("clarify", chat("What is its phone number?", id).path("mode").asText());
        verify(mapper, times(1)).findEnabled();
        upstream.verify();
    }

    @ParameterizedTest @ValueSource(strings = {"Where can I download this?", "Who can I contact?", "When is the deadline?"})
    void commonDemonstrativesAndEllipticalRequestsMustResolveBeforeLexicalRetrieval(String followUp) throws Exception {
        generation("Academic calendar 2026 2027", 1, "Calendar [FAQ:1]");
        rewrite("follow_up", "Academic calendar 2026 2027", "Academic calendar 2026 2027");
        generation("Academic calendar 2026 2027", 1, "Current calendar evidence [FAQ:1]");
        String id = chat("Academic calendar 2026 2027", null).path("conversationId").asText();
        assertEquals(1, chat(followUp, id).path("faqId").asInt());
        upstream.verify();
    }

    @Test void successfulStoredAnswerAfterGenerationTimeoutStillEstablishesTheNewTopic() throws Exception {
        configure(true, 450);
        generation("Undergraduate course selection contact", 4, "Contact [FAQ:4]");
        upstream.expect(anything()).andRespond(request -> {
            try { Thread.sleep(1500); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            return withSuccess(envelope("{\"answer\":\"Calendar [FAQ:1]\",\"citedFaqIds\":[1]}"), MediaType.APPLICATION_JSON).createResponse(request);
        });
        rewrite("follow_up", "Academic calendar 2026 2027", "Academic calendar 2026 2027");
        generation("Academic calendar 2026 2027", 1, "Calendar [FAQ:1]");
        String id = chat("Undergraduate course selection contact", null).path("conversationId").asText();
        assertEquals("extractive", chat("Academic calendar 2026 2027", id).path("mode").asText());
        assertEquals(1, chat("Where is it?", id).path("faqId").asInt());
        upstream.verify();
    }

    @ParameterizedTest @ValueSource(strings = {
            "{}", "[]", "null", "{\"action\":\"follow_up\",\"question\":123}",
            "{\"action\":\"invent\",\"question\":\"Undergraduate course selection contact\"}",
            "{\"action\":\"clarify\",\"question\":\"An invented answer\"}",
            "{\"action\":\"follow_up\",\"question\":\"[FAQ:4]\"}",
            "{\"action\":\"follow_up\",\"question\":\"Undergraduate course selection contact\",\"extra\":true}",
            "{\"action\":\"follow_up\",\"question\":\"\"}",
            "{\"action\":\"follow_up\",\"question\":\"What is its phone number?\"}"
    })
    void malformedOrUnresolvedRewriteNeverReachesRetrievalOrGeneration(String result) throws Exception {
        generation("Undergraduate course selection contact", 4, "Prior answer [FAQ:4]");
        upstream.expect(anything()).andRespond(withSuccess(envelope(result), MediaType.APPLICATION_JSON));
        String id = chat("Undergraduate course selection contact", null).path("conversationId").asText();
        assertEquals("clarify", chat("What is its phone number?", id).path("mode").asText());
        verify(mapper, times(1)).findEnabled();
        upstream.verify();
    }

    @Test void clearingDuringRewritePreventsLateGenerationAndHistoryRestoration() throws Exception {
        generation("Undergraduate course selection contact", 4, "Prior answer [FAQ:4]");
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        upstream.expect(anything()).andRespond(request -> {
            entered.countDown();
            try { release.await(2, TimeUnit.SECONDS); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            return withSuccess(envelope("{\"action\":\"follow_up\",\"question\":\"Undergraduate course selection contact\"}"), MediaType.APPLICATION_JSON).createResponse(request);
        });
        String id = chat("Undergraduate course selection contact", null).path("conversationId").asText();
        ExecutorService caller = Executors.newSingleThreadExecutor();
        try {
            Future<Integer> pending = caller.submit(() -> mvc.perform(post("/faq/chat").session(session)
                    .contentType(MediaType.APPLICATION_JSON).content(json.createObjectNode()
                            .put("question", "What is its phone number?").put("conversationId", id).toString()))
                    .andReturn().getResponse().getStatus());
            assertTrue(entered.await(1, TimeUnit.SECONDS));
            mvc.perform(delete("/faq/chat/current").session(session)).andExpect(status().isOk());
            release.countDown();
            assertEquals(404, pending.get(1, TimeUnit.SECONDS));
            assertNull(session.getAttribute("faq.conversation"));
            verify(mapper, times(1)).findEnabled();
            upstream.verify();
        } finally { release.countDown(); caller.shutdownNow(); }
    }

    @Test void rewriteAndGenerationShareDeadlineAndTimeoutReturnsClarificationInsteadOfOldFacts() throws Exception {
        configure(true, 300);
        generation("Undergraduate course selection contact", 4, "Old facts [FAQ:4]");
        upstream.expect(anything()).andRespond(request -> {
            try { Thread.sleep(1200); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            return withSuccess(envelope("{\"action\":\"follow_up\",\"question\":\"Undergraduate course selection contact\"}"), MediaType.APPLICATION_JSON).createResponse(request);
        });
        String id = chat("Undergraduate course selection contact", null).path("conversationId").asText();
        assertTimeout(java.time.Duration.ofMillis(950), () -> assertEquals("clarify", chat("What is its phone number?", id).path("mode").asText()));
        verify(mapper, times(1)).findEnabled();
        upstream.verify();
    }

    private void rewrite(String action, String question, String lastTopic) throws Exception {
        upstream.expect(anything()).andExpect(jsonPath("$.max_tokens").value(128))
                .andExpect(jsonPath("$.thinking.type").value("disabled"))
                .andExpect(request -> {
                    JsonNode body = json.readTree(((MockClientHttpRequest) request).getBodyAsBytes());
                    JsonNode input = json.readTree(body.path("messages").get(1).path("content").asText());
                    assertTrue(input.path("history").size() <= 3);
                    assertEquals(lastTopic, input.path("history").get(input.path("history").size()-1).path("question").asText());
                    assertFalse(body.toString().contains("PRIVATE_ACCOUNT"));
                    assertFalse(body.toString().contains("Old phone 999999"));
                    assertFalse(body.toString().contains("Old answer"));
                }).andRespond(withSuccess(envelope(json.createObjectNode().put("action", action).put("question", question).toString()), MediaType.APPLICATION_JSON));
    }
    private void generation(String question, int id, String answer) throws Exception {
        upstream.expect(anything()).andExpect(jsonPath("$.max_tokens").value(512)).andExpect(request -> {
            JsonNode body = json.readTree(((MockClientHttpRequest) request).getBodyAsBytes());
            JsonNode input = json.readTree(body.path("messages").get(1).path("content").asText());
            assertEquals(question, input.path("question").asText());
            assertFalse(input.has("history"));
            assertTrue(input.path("contexts").size() > 0);
        }).andRespond(withSuccess(envelope(json.createObjectNode().put("answer", answer).set("citedFaqIds", json.createArrayNode().add(id)).toString()), MediaType.APPLICATION_JSON));
    }
    private JsonNode chat(String question, String id) throws Exception {
        String request = json.createObjectNode().put("question", question).put("conversationId", id).toString();
        String body = mvc.perform(post("/faq/chat").session(session).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode data = json.readTree(body).path("data");
        assertFalse(data.has("history")); assertFalse(data.has("resolvedQuestion"));
        return data;
    }
    private String envelope(String content) {
        return json.createObjectNode().set("choices", json.createArrayNode().add(json.createObjectNode()
                .put("finish_reason", "stop").set("message", json.createObjectNode().put("role", "assistant").put("content", content)))).toString();
    }
    private FaqEntry entry(long id, String question) {
        FaqEntry row = new FaqEntry(); row.setId(id); row.setEnabled(true); row.setQuestion(question);
        row.setAnswer("Current stored answer " + id); row.setSourceTitle("Official fixture " + id);
        row.setSourceUrl("https://jwc.ccnu.edu.cn/fixture/" + id); return row;
    }
}
