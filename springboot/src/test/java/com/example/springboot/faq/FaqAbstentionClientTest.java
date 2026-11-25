package com.example.springboot.faq;

import com.example.springboot.faq.config.FaqLlmProperties;
import com.example.springboot.faq.entity.FaqEntry;
import com.example.springboot.faq.llm.LlmClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FaqAbstentionClientTest {
    private final ObjectMapper json = new ObjectMapper();
    private MockRestServiceServer server;
    private LlmClient client;

    @BeforeEach
    void setUp() {
        client = new LlmClient(new FaqLlmProperties(true, "https://model.example/v1", "test-key",
                "fixture-model", "max_completion_tokens"), json,
                new RestTemplateBuilder().additionalCustomizers(
                        template -> server = MockRestServiceServer.bindTo(template).build()));
    }

    @Test
    void acceptsExplicitInsufficientEvidenceWithoutAnswerOrCitations() {
        respond("{\"action\":\"insufficient_evidence\",\"answer\":\"\",\"citedFaqIds\":[]}");
        LlmClient.Generation result = generate("What is the current student card replacement fee?");
        assertTrue(result.isInsufficientEvidence());
        assertEquals("", result.getAnswer());
        assertTrue(result.getCitedFaqIds().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> result.getCitedFaqIds().add(7L));
        server.verify();
    }

    @Test
    void keepsLegacyGroundedAnswersAndImmutableCitations() {
        respond("{\"answer\":\"Contact student support. [FAQ:7]\",\"citedFaqIds\":[7]}");
        LlmClient.Generation result = generate();
        assertFalse(result.isInsufficientEvidence());
        assertEquals("Contact student support. [FAQ:7]", result.getAnswer());
        assertEquals(Collections.singletonList(7L), result.getCitedFaqIds());
        assertThrows(UnsupportedOperationException.class, () -> result.getCitedFaqIds().clear());
        server.verify();
    }

    @Test
    void projectsOfficialAddressesAsSourceNavigationWithoutChangingStoredEvidence() throws Exception {
        FaqEntry evidence=new FaqEntry(); evidence.setId(7L); evidence.setQuestion("How does the published VPN guide work?");
        evidence.setAnswer("Reinstall the current aTrust app and use https://vpn.ccnu.edu.cn. The platform ms.ccnu.edu.cn has download and help sections.");
        evidence.setEnabled(true); String original=evidence.getAnswer();
        ObjectNode completion=json.createObjectNode();
        completion.putArray("choices").addObject().put("finish_reason","stop").putObject("message")
                .put("role","assistant").put("content","{\"answer\":\"Reinstall the current aTrust app; see Sources for the official address. [FAQ:7]\",\"citedFaqIds\":[7]}");
        server.expect(requestTo("https://model.example/v1/chat/completions")).andExpect(request->{
            com.fasterxml.jackson.databind.JsonNode body=json.readTree(((MockClientHttpRequest)request).getBodyAsBytes());
            String projected=json.readTree(body.path("messages").get(1).path("content").asText())
                    .path("contexts").get(0).path("answer").asText();
            assertFalse(projected.contains("ccnu.edu.cn"));
            assertFalse(projected.contains("https://"));
            assertTrue(projected.contains("Sources"));
            assertTrue(projected.contains("Reinstall the current aTrust app"));
            assertTrue(projected.contains("download and help sections"));
            assertTrue(body.path("messages").get(0).path("content").asText().contains("apply explicit numeric conditions"));
        }).andRespond(withSuccess(completion.toString(),MediaType.APPLICATION_JSON));
        assertFalse(client.generate("How does the published VPN guide work?",Collections.singletonList(evidence)).isInsufficientEvidence());
        assertEquals(original,evidence.getAnswer()); server.verify();
    }

    @Test
    void sendsBalancedEvidenceInstructionsWithoutChangingOutputOrCallBudget() {
        ObjectNode completion = json.createObjectNode();
        completion.putArray("choices").addObject().put("finish_reason", "stop").putObject("message")
                .put("role", "assistant").put("content", "{\"answer\":\"Use the published recovery instructions. [FAQ:7]\",\"citedFaqIds\":[7]}");
        server.expect(requestTo("https://model.example/v1/chat/completions"))
                .andExpect(request -> {
                    com.fasterxml.jackson.databind.JsonNode body = json.readTree(((MockClientHttpRequest) request).getBodyAsBytes());
                    String system = body.path("messages").get(0).path("content").asText();
                    assertTrue(system.contains("equivalent wording"), "Paraphrases must not require identical words");
                    assertTrue(system.contains("recovery or reset instructions"), "Public procedure is distinct from a secret value");
                    assertTrue(system.contains("Sources"), "Navigation must remain possible without a generated URL");
                    assertTrue(system.contains("perform or confirm an action"), "Unavailable operations require structured refusal");
                    assertTrue(system.contains("how-to question"), "Supported guidance differs from performing an operation");
                    assertTrue(system.contains("requested version is unsupported"));
                    assertTrue(system.contains("insufficient_evidence"));
                    assertEquals(512, body.path("max_completion_tokens").asInt());
                    assertEquals(2, body.path("messages").size());
                    assertFalse(body.has("tools"));
                }).andRespond(withSuccess(completion.toString(), MediaType.APPLICATION_JSON));
        FaqEntry evidence = new FaqEntry(); evidence.setId(7L); evidence.setEnabled(true);
        evidence.setQuestion("Recover a forgotten central password");
        evidence.setAnswer("Use the published recovery instructions.");
        assertFalse(client.generate("How do I regain access to central sign-in?", Collections.singletonList(evidence)).isInsufficientEvidence());
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"action\":\"answer\",\"answer\":\"Contact support. [FAQ:7]\",\"citedFaqIds\":[7]}",
            "{\"action\":\"INSUFFICIENT_EVIDENCE\",\"answer\":\"\",\"citedFaqIds\":[]}",
            "{\"action\":null,\"answer\":\"\",\"citedFaqIds\":[]}",
            "{\"action\":7,\"answer\":\"\",\"citedFaqIds\":[]}",
            "{\"action\":\"insufficient_evidence\",\"answer\":\"There is no information.\",\"citedFaqIds\":[]}",
            "{\"action\":\"insufficient_evidence\",\"answer\":\" \",\"citedFaqIds\":[]}",
            "{\"action\":\"insufficient_evidence\",\"answer\":null,\"citedFaqIds\":[]}",
            "{\"action\":\"insufficient_evidence\",\"answer\":\"\",\"citedFaqIds\":[7]}",
            "{\"action\":\"insufficient_evidence\",\"answer\":\"\",\"citedFaqIds\":[999]}",
            "{\"action\":\"insufficient_evidence\",\"answer\":\"\",\"citedFaqIds\":null}",
            "{\"action\":\"insufficient_evidence\",\"answer\":\"\",\"citedFaqIds\":{}}",
            "{\"action\":\"insufficient_evidence\",\"answer\":\"\"}",
            "{\"action\":\"insufficient_evidence\",\"citedFaqIds\":[]}",
            "{\"action\":\"insufficient_evidence\",\"answer\":\"\",\"citedFaqIds\":[],\"extra\":true}",
            "{\"reason\":\"insufficient_evidence\",\"answer\":\"\",\"citedFaqIds\":[]}",
            "{\"action\":\"insufficient_evidence\",\"action\":\"insufficient_evidence\",\"answer\":\"\",\"citedFaqIds\":[]}",
            "{\"action\":\"insufficient_evidence\",\"answer\":\"\",\"citedFaqIds\":[]} {}",
            "{\"answer\":\"\",\"citedFaqIds\":[]}"
    })
    void rejectsMalformedOrContradictoryAbstentions(String content) {
        assertInvalid(content);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"answer\":\"Contact support. [FAQ:999]\",\"citedFaqIds\":[999]}",
            "{\"answer\":\"Contact support. [FAQ:7]\",\"citedFaqIds\":[7,7]}",
            "{\"answer\":\"Contact support. [FAQ:7]\",\"citedFaqIds\":[]}",
            "{\"answer\":\"Contact support. [FAQ:999]\",\"citedFaqIds\":[7]}",
            "{\"answer\":\"Contact support. [FAQ:7]\",\"citedFaqIds\":[7],\"extra\":true}",
            "{\"answer\":\"See https://invented.example. [FAQ:7]\",\"citedFaqIds\":[7]}"
    })
    void preservesLegacyCitationAndLinkValidation(String content) {
        assertInvalid(content);
    }

    private void assertInvalid(String content) {
        respond(content);
        LlmClient.LlmException error = assertThrows(LlmClient.LlmException.class, this::generate);
        assertEquals(LlmClient.Type.INVALID_RESPONSE, error.getType());
        server.verify();
    }

    private void respond(String content) {
        ObjectNode completion = json.createObjectNode();
        ObjectNode choice = completion.putArray("choices").addObject().put("finish_reason", "stop");
        choice.putObject("message").put("role", "assistant").put("content", content);
        server.expect(requestTo("https://model.example/v1/chat/completions"))
                .andRespond(withSuccess(completion.toString(), MediaType.APPLICATION_JSON));
    }

    private LlmClient.Generation generate() {
        return generate("Who can help with student services?");
    }

    private LlmClient.Generation generate(String question) {
        FaqEntry evidence = new FaqEntry();
        evidence.setId(7L);
        evidence.setQuestion("Who can help with student services?");
        evidence.setAnswer("Contact student support.");
        evidence.setEnabled(true);
        return client.generate(question, Collections.singletonList(evidence));
    }
}
