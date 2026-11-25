package com.example.springboot.faq;

import com.example.springboot.faq.config.FaqLlmProperties;
import com.example.springboot.faq.llm.LlmClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Explicit paid smoke test, excluded from ordinary mvn test; synthetic auth is not browser sign-in. */
@SpringBootTest(properties = {"logging.level.root=WARN", "spring.main.banner-mode=off"})
@AutoConfigureMockMvc
@ActiveProfiles("faq-deepseek")
@EnabledIfEnvironmentVariable(named = "FAQ_C5_LIVE_VERIFY", matches = "true")
class FaqLiveMultiTurnIT {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private FaqLlmProperties config;
    @Autowired private LlmClient client;

    @Test void realFollowUpTopicSwitchClarificationAndNoMatch() throws Exception {
        assertEquals("deepseek-flash", config.getModel());
        assertEquals("https://api.deepseek.com/chat/completions", config.getEndpoint().toString());
        assertTrue(config.isEnabled());
        assertEquals(System.getenv("DEEPSEEK_API_KEY"), config.getApiKey());
        RestTemplate http = (RestTemplate) ReflectionTestUtils.getField(client, "http");
        ClientHttpRequestFactory original = http.getRequestFactory();
        AtomicInteger calls = new AtomicInteger();
        http.setRequestFactory((uri, method) -> {
            if (calls.incrementAndGet() > 6) throw new AssertionError("Live C5 request cap exceeded");
            return original.createRequest(uri, method);
        });
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("User", "C5_SYNTHETIC_USER"); session.setAttribute("Identity", "stu");
        try {
            JsonNode first = chat(session, "Who can I contact about undergraduate course selection?", null);
            assertGenerated(first, 4, "027-67863133");
            String id = first.path("conversationId").asText();
            assertGenerated(chat(session, "What is its phone number?", id), 4, "027-67863133");
            assertGenerated(chat(session, "Where can I find the CCNU academic calendar for 2026-2027?", id), 1, "2026");
            assertGenerated(chat(session, "Where can I find it?", id), 1, "2026");
            assertEquals("no_match", chat(session, "volcano eruption", id).path("mode").asText());
            assertEquals("clarify", chat(session, "What is its phone number?", id).path("mode").asText());
            assertEquals("clarify", chat(session, "Who can I contact?", null).path("mode").asText());
            assertEquals(6, calls.get());
            System.out.println("C5_LIVE PASS: real follow-ups, fresh evidence, topic switch, clarification and no_match.");
        } finally {
            http.setRequestFactory(original);
            System.out.println("C5_LIVE providerRequests=" + calls.get());
        }
    }

    private JsonNode chat(MockHttpSession session, String question, String id) throws Exception {
        String response = mvc.perform(post("/faq/chat").session(session).contentType(MediaType.APPLICATION_JSON)
                .content(json.createObjectNode().put("question", question).put("conversationId", id).toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode result = json.readTree(response).path("data");
        System.out.println("C5_LIVE " + result); // Public answer fields only; no request headers or credentials.
        return result;
    }
    private void assertGenerated(JsonNode result, long id, String fact) {
        assertEquals("generated", result.path("mode").asText());
        assertEquals(id, result.path("faqId").asLong());
        assertTrue(result.path("answer").asText().contains(fact));
        if (id == 1) assertTrue(result.path("answer").asText().contains("2027"));
        assertTrue(result.path("answer").asText().contains("[FAQ:" + id + "]"));
        assertEquals(id, result.path("sources").get(0).path("faqId").asLong());
        assertTrue(result.path("sources").get(0).path("sourceUrl").asText().startsWith("https://jwc.ccnu.edu.cn/"));
    }
}
