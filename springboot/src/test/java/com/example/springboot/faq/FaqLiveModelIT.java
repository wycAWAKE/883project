package com.example.springboot.faq;

import com.example.springboot.faq.config.FaqLlmProperties;
import com.example.springboot.faq.entity.FaqEntry;
import com.example.springboot.faq.mapper.FaqMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Explicit opt-in; two real free-model calls, SELECT-only database access, no provider mocks. */
@EnabledIfEnvironmentVariable(named = "FAQ_LIVE_VERIFY", matches = "true")
@ActiveProfiles("faq-glm")
@SpringBootTest(properties = {"spring.main.banner-mode=off", "logging.level.root=WARN"})
@AutoConfigureMockMvc
class FaqLiveModelIT {
    @Autowired private MockMvc http;
    @Autowired private ObjectMapper json;
    @Autowired private FaqMapper mapper;
    @Autowired private FaqLlmProperties model;

    @Test
    void realKnowledgeAndFreeModelProduceCitedAnswersThroughTheChatApi() throws Exception {
        assertTrue(model.isEnabled());
        assertTrue("glm-4.7-flash".equals(model.getModel()) || "glm-4-flash-250414".equals(model.getModel()),
                "This opt-in check permits only the two verified free Flash model IDs");
        assertEquals("https://open.bigmodel.cn/api/paas/v4/chat/completions", model.getEndpoint().toString());
        List<FaqEntry> entries = mapper.findEnabled();
        MockHttpSession session = new MockHttpSession();
        // Synthetic authentication context; real browser login is verified separately.
        session.setAttribute("User", "faq-live-verification");
        session.setAttribute("Identity", "stu");
        String[] questions = {"Where can I find the CCNU academic calendar for 2026-2027?",
                "Who can I contact about undergraduate course selection?"};
        long[] expectedIds = {1L, 4L};
        String conversationId = null;
        try {
            for (int i = 0; i < questions.length; i++) {
                long started = System.nanoTime();
                JsonNode data = chat(session, questions[i], conversationId);
                conversationId = data.path("conversationId").asText();
                assertEquals("generated", data.path("mode").asText(), "A fallback is not live-generation success");
                assertTrue(data.path("matched").asBoolean());
                assertTrue(data.path("notice").isNull());
                assertFalse(conversationId.isEmpty());
                assertFalse(data.path("answer").asText().isEmpty());
                boolean expectedSource = false;
                for (JsonNode source : data.path("sources")) {
                    FaqEntry stored = entries.stream().filter(entry -> entry.getId() == source.path("faqId").asLong())
                            .findFirst().orElseThrow(() -> new AssertionError("Unknown source"));
                    assertEquals(stored.getSourceUrl(), source.path("sourceUrl").asText());
                    assertEquals(stored.getSourceTitle(), source.path("sourceTitle").asText());
                    expectedSource |= stored.getId() == expectedIds[i];
                }
                assertTrue(expectedSource);
                String answer = data.path("answer").asText();
                if (i == 0) { assertTrue(answer.contains("2026")); assertTrue(answer.contains("2027")); }
                else assertTrue(answer.contains("027-67863133"));
                System.out.println("LIVE " + model.getModel() + " " + ((System.nanoTime() - started) / 1000000)
                        + "ms " + data);
            }
            JsonNode missing = chat(session, "volcano eruption", conversationId);
            assertEquals("no_match", missing.path("mode").asText());
            assertEquals(0, missing.path("sources").size());
            System.out.println("LIVE no_match verified");
        } finally {
            http.perform(delete("/faq/chat/current").session(session)).andExpect(status().isOk());
        }
    }

    private JsonNode chat(MockHttpSession session, String question, String id) throws Exception {
        String body = json.createObjectNode().put("question", question).put("conversationId", id).toString();
        String response = http.perform(post("/faq/chat").session(session).contentType("application/json").content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode result = json.readTree(response);
        assertEquals("0", result.path("code").asText());
        return result.path("data");
    }
}
