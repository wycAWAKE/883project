package com.example.springboot.faq;

import com.example.springboot.faq.config.FaqLlmProperties;
import com.example.springboot.faq.entity.FaqEntry;
import com.example.springboot.faq.llm.LlmClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;

import java.net.SocketTimeoutException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class FaqLlmClientTest {
    private static final String ENDPOINT = "https://model.example/v1/chat/completions";
    private final ObjectMapper json = new ObjectMapper();
    private MockRestServiceServer server;
    private LlmClient client;

    @BeforeEach
    void setUp() {
        client = client(properties(true, "https://model.example/v1", "max_completion_tokens"));
    }

    private LlmClient client(FaqLlmProperties props) {
        return new LlmClient(props, json, new RestTemplateBuilder().additionalCustomizers(
                template -> server = MockRestServiceServer.bindTo(template).build()));
    }

    @Test
    void projectsOnlyPublicEvidenceAndReturnsValidatedAnswer() throws Exception {
        server.expect(requestTo(ENDPOINT)).andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(header("Content-Type", "application/json"))
                .andExpect(request -> {
                    JsonNode body = json.readTree(((MockClientHttpRequest) request).getBodyAsBytes());
                    assertEquals("fixture-model", body.path("model").asText());
                    assertEquals(512, body.path("max_completion_tokens").intValue());
                    assertFalse(body.has("max_tokens"));
                    assertFalse(body.has("thinking"));
                    assertFalse(body.path("stream").asBoolean(true));
                    assertEquals("json_object", body.path("response_format").path("type").asText());
                    assertEquals(2, body.path("messages").size());
                    assertEquals("system", body.path("messages").get(0).path("role").asText());
                    assertTrue(body.path("messages").get(0).path("content").asText().contains("English"));
                    assertTrue(body.path("messages").get(0).path("content").asText().contains(
                            "{\"answer\":\"Evidence-based answer. [FAQ:7]\",\"citedFaqIds\":[7]}"));
                    JsonNode input = json.readTree(body.path("messages").get(1).path("content").asText());
                    assertEquals("Where is the calendar?", input.path("question").asText());
                    JsonNode evidence = input.path("contexts").get(0);
                    assertEquals(3, evidence.size());
                    assertEquals(7, evidence.path("id").asLong());
                    assertEquals("Calendar?", evidence.path("question").asText());
                    assertEquals("See the calendar page.", evidence.path("answer").asText());
                    assertFalse(body.toString().contains("DO_NOT_SEND"));
                    assertFalse(body.has("tools"));
                    assertFalse(request.getHeaders().containsKey("Cookie"));
                }).andRespond(withSuccess(completion("Use the calendar page. [FAQ:7]", "[7]"), MediaType.APPLICATION_JSON));
        LlmClient.Generation result = client.generate("Where is the calendar?", Collections.singletonList(evidence()));
        assertEquals("Use the calendar page. [FAQ:7]", result.getAnswer());
        assertEquals(Collections.singletonList(7L), result.getCitedFaqIds());
        server.verify();
    }

    @Test
    void explicitLegacyOutputParameterDoesNotRetryWithAnotherParameter() {
        client = client(properties(true, "https://model.example/v1/", "max_tokens"));
        server.expect(requestTo(ENDPOINT)).andExpect(jsonPath("$.max_tokens").value(512))
                .andExpect(jsonPath("$.max_completion_tokens").doesNotExist())
                .andRespond(withSuccess(completion("See page. [FAQ:7]", "[7]"), MediaType.APPLICATION_JSON));
        assertEquals("See page. [FAQ:7]", generate().getAnswer());
        server.verify();
    }

    @Test
    void glmFlashUsesTheAnswerBudgetWithoutSpendingItOnThinking() {
        client = client(new FaqLlmProperties(true, "https://open.bigmodel.cn/api/paas/v4",
                "test-key", "glm-4.7-flash", "max_tokens"));
        server.expect(requestTo("https://open.bigmodel.cn/api/paas/v4/chat/completions"))
                .andExpect(jsonPath("$.thinking.type").value("disabled"))
                .andExpect(jsonPath("$.max_tokens").value(512))
                .andExpect(jsonPath("$.response_format.type").value("json_object"))
                .andRespond(withSuccess(completion("See page. [FAQ:7]", "[7]"), MediaType.APPLICATION_JSON));
        assertEquals("See page. [FAQ:7]", generate().getAnswer());
        server.verify();
    }

    @Test
    void explicitGlmProfileEnablesTheFreeModelWithBackendOnlyCredentials() {
        new ApplicationContextRunner()
                .withInitializer(new org.springframework.boot.test.context.ConfigDataApplicationContextInitializer())
                .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class))
                .withUserConfiguration(FaqLlmProperties.class)
                .withPropertyValues("spring.profiles.active=faq-glm", "faq.llm.api-key=test-key")
                .run(app -> {
                    assertNull(app.getStartupFailure());
                    FaqLlmProperties props = app.getBean(FaqLlmProperties.class);
                    assertTrue(props.isEnabled());
                    assertEquals("glm-4-flash-250414", props.getModel());
                    assertEquals("https://open.bigmodel.cn/api/paas/v4/chat/completions", props.getEndpoint().toString());
                    assertEquals("max_tokens", props.getOutputTokenParameter());
                });
    }

    @Test
    void deepSeekFlashUsesJsonOutputWithoutThinking() {
        client = client(new FaqLlmProperties(true, "https://api.deepseek.com",
                "deepseek-test-key", "deepseek-flash", "max_tokens"));
        server.expect(requestTo("https://api.deepseek.com/chat/completions"))
                .andExpect(header("Authorization", "Bearer deepseek-test-key"))
                .andExpect(jsonPath("$.thinking.type").value("disabled"))
                .andExpect(jsonPath("$.max_tokens").value(512))
                .andExpect(jsonPath("$.response_format.type").value("json_object"))
                .andRespond(withSuccess(completion("See page. [FAQ:7]", "[7]"), MediaType.APPLICATION_JSON));
        assertEquals("See page. [FAQ:7]", generate().getAnswer());
        server.verify();
    }

    @Test
    void deepSeekProfileRequiresItsOwnKeyInsteadOfReusingAnotherProvidersKey() {
        Map<String, Object> env = new HashMap<>();
        env.put("FAQ_LLM_API_KEY", "old-provider-key");
        env.put("FAQ_LLM_BASE_URL", "https://old-provider.example/v1");
        env.put("FAQ_LLM_MODEL", "old-provider-model");
        ApplicationContextRunner context = new ApplicationContextRunner()
                .withInitializer(new org.springframework.boot.test.context.ConfigDataApplicationContextInitializer())
                .withInitializer(app -> app.getEnvironment().getPropertySources()
                        // Replace the real host environment, so an installed private key cannot satisfy this missing-key case.
                        .replace("systemEnvironment", new SystemEnvironmentPropertySource("systemEnvironment", env)))
                .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class))
                .withUserConfiguration(FaqLlmProperties.class)
                .withPropertyValues("spring.profiles.active=faq-deepseek");
        context.run(app -> assertNotNull(app.getStartupFailure()));
        env.put("DEEPSEEK_API_KEY", "deepseek-test-key");
        context.run(app -> {
            assertNull(app.getStartupFailure());
            FaqLlmProperties props = app.getBean(FaqLlmProperties.class);
            assertTrue(props.isEnabled());
            assertEquals("https://api.deepseek.com/chat/completions", props.getEndpoint().toString());
            assertEquals("deepseek-flash", props.getModel());
            assertEquals("deepseek-test-key", props.getApiKey());
            assertEquals("max_tokens", props.getOutputTokenParameter());
        });
    }

    @Test
    void disabledClientAndMissingEvidenceNeverOpenHttpConnection() {
        client = client(new FaqLlmProperties(false, "", "", "", "max_completion_tokens"));
        assertFailure("DISABLED", () -> generate());
        client = client(properties(true, "https://model.example/v1", "max_completion_tokens"));
        assertFailure("INVALID_INPUT", () -> client.generate("question", Collections.emptyList()));
        assertFailure("INVALID_INPUT", () -> client.generate(" \u0085 ", Collections.singletonList(evidence())));
        assertFailure("INVALID_INPUT", () -> client.generate(repeat("x", 1001), Collections.singletonList(evidence())));
        server.verify();
    }

    @Test
    void rejectsUnboundedOrInvalidContextsBeforeNetwork() {
        FaqEntry item = evidence();
        item.setAnswer(repeat("x", 2001));
        assertFailure("INVALID_INPUT", () -> client.generate("question", Collections.singletonList(item)));
        item.setAnswer("answer"); item.setEnabled(false);
        assertFailure("INVALID_INPUT", () -> client.generate("question", Collections.singletonList(item)));
        item.setEnabled(true); item.setId(null);
        assertFailure("INVALID_INPUT", () -> client.generate("question", Collections.singletonList(item)));
        assertFailure("INVALID_INPUT", () -> client.generate("question", Arrays.asList(evidence(), evidence())));
        server.verify();
    }

    @Test
    void includesJsonEscapingInWholeRequestByteBudget() {
        // Each control character is escaped to six bytes; no HTTP on overflow.
        FaqEntry item = evidence();
        item.setQuestion("q"); item.setAnswer(repeat("\u0001", 1999));
        FaqEntry second = evidence(); second.setId(8L); second.setQuestion("q"); second.setAnswer(repeat("\u0001", 1999));
        FaqEntry third = evidence(); third.setId(9L); third.setQuestion("q"); third.setAnswer(repeat("\u0001", 1999));
        assertFailure("REQUEST_TOO_LARGE", () -> client.generate("question", Arrays.asList(item, second, third)));
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 429, 500, 503, 302, 307})
    void upstreamErrorsAndRedirectsAreSanitizedAndNeverRetried(int status) {
        server.expect(requestTo(ENDPOINT)).andRespond(withStatus(HttpStatus.valueOf(status))
                .location(java.net.URI.create("https://attacker.example/collect"))
                .body("DO_NOT_SEND test-key upstream private details"));
        String type = status == 401 || status == 403 ? "AUTHENTICATION"
                : status == 429 ? "RATE_LIMITED" : "UPSTREAM";
        assertFailure(type, this::generate);
        server.verify();
    }

    @Test
    void socketTimeoutDoesNotExposeTransportExceptionOrRetry() {
        server.expect(requestTo(ENDPOINT)).andRespond(request -> {
            throw new ResourceAccessException("test-key DO_NOT_SEND", new SocketTimeoutException("private"));
        });
        assertFailure("TIMEOUT", this::generate);
        server.verify();
    }

    @Test
    void networkErrorDoesNotExposeTransportExceptionOrRetry() {
        server.expect(requestTo(ENDPOINT)).andRespond(request -> {
            throw new ResourceAccessException("test-key DO_NOT_SEND");
        });
        assertFailure("NETWORK", this::generate);
        server.verify();
    }

    @Test
    void productionTransportUsesBoundedSocketTimeoutsAndDisablesRedirects() throws Exception {
        new LlmClient(properties(true, "https://model.example/v1", "max_completion_tokens"), json,
                new RestTemplateBuilder().additionalCustomizers(template -> {
                    Object factory = template.getRequestFactory();
                    assertEquals(3000, org.springframework.test.util.ReflectionTestUtils.getField(factory, "connectTimeout"));
                    assertEquals(30000, org.springframework.test.util.ReflectionTestUtils.getField(factory, "readTimeout"));
                    try {
                        java.net.HttpURLConnection connection = new java.net.HttpURLConnection(
                                new java.net.URL("https://model.example/v1")) {
                            public void connect() { }
                            public void disconnect() { }
                            public boolean usingProxy() { return false; }
                        };
                        connection.setInstanceFollowRedirects(true);
                        org.springframework.test.util.ReflectionTestUtils.invokeMethod(factory, "prepareConnection", connection, "POST");
                        assertEquals("POST", connection.getRequestMethod());
                        assertFalse(connection.getInstanceFollowRedirects());
                    } catch (java.net.MalformedURLException error) {
                        throw new AssertionError(error);
                    }
                }));
    }

    @Test
    void rejectsRefusalAndToolCallsEvenWhenAnswerAndFinishReasonLookValid() throws Exception {
        String valid = completion("Page [FAQ:7]", "[7]");
        rejectsResponse(valid.replace("\"refusal\":null", "\"refusal\":\"Cannot answer\""));
        setUp();
        rejectsResponse(valid.replace("\"refusal\":null", "\"refusal\":null,\"tool_calls\":[{}]"));
    }

    @Test
    void acceptsExplicitNullOptionalToolFieldsWithoutTreatingThemAsToolCalls() {
        server.expect(requestTo(ENDPOINT)).andRespond(withSuccess(
                completion("Page [FAQ:7]", "[7]").replace("\"refusal\":null",
                        "\"refusal\":null,\"tool_calls\":null,\"function_call\":null"),
                MediaType.APPLICATION_JSON));
        assertEquals("Page [FAQ:7]", generate().getAnswer());
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}", "{broken", "{\"choices\":[]}", "{\"choices\":[{}]}",
            "{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"role\":\"assistant\",\"content\":\"{}\"}}]}",
            "{\"choices\":[{\"finish_reason\":\"tool_calls\",\"message\":{\"role\":\"assistant\",\"content\":\"{}\"}}]}",
            "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"role\":\"assistant\",\"content\":null}}]}"
    })
    void rejectsMalformedIncompleteAndTruncatedCompletions(String response) {
        rejectsResponse(response);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}", "{\"answer\":\"\",\"citedFaqIds\":[7]}",
            "{\"answer\":\"unsupported [FAQ:99]\",\"citedFaqIds\":[99]}",
            "{\"answer\":\"missing marker\",\"citedFaqIds\":[7]}",
            "{\"answer\":\"wrong marker [FAQ:8]\",\"citedFaqIds\":[7]}",
            "{\"answer\":\"See [FAQ:7]\",\"citedFaqIds\":[]}",
            "{\"answer\":\"See [FAQ:7]\",\"citedFaqIds\":[\"7\"]}",
            "{\"answer\":\"See [FAQ:7]\",\"citedFaqIds\":[7,7]}",
            "{\"answer\":7,\"citedFaqIds\":[7]}",
            "{\"answer\":\"See [FAQ:7]\",\"citedFaqIds\":[7.0]}",
            "{\"answer\":\"See [FAQ:7] [FAQ:07]\",\"citedFaqIds\":[7]}",
            "{\"answer\":\"See [FAQ:7] [FAQ:9223372036854775808]\",\"citedFaqIds\":[7]}",
            "{\"answer\":\"See [FAQ:7]\",\"citedFaqIds\":[7],\"sourceUrl\":\"https://fake.example\"}",
            "{\"answer\":\"See [FAQ:7]\",\"citedFaqIds\":[7]} {}",
            "{\"answer\":\"See [FAQ:7]\",\"answer\":\"overridden [FAQ:7]\",\"citedFaqIds\":[7]}"
    })
    void rejectsInvalidAnswerStructureAndCitationIds(String content) throws Exception {
        rejectsResponse(envelope(content));
    }

    @Test
    void rejectsOversizedResponse() {
        rejectsResponse(repeat(" ", 65537));
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 429})
    void realTransportAbortsRejectedBodyWithoutDrainingItsSlowTail(int status) throws Exception {
        com.sun.net.httpserver.HttpServer local = com.sun.net.httpserver.HttpServer.create(
                new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        java.util.concurrent.CountDownLatch releaseTail = new java.util.concurrent.CountDownLatch(1);
        local.createContext("/completion", exchange -> {
            try {
                exchange.getRequestBody().close();
                exchange.sendResponseHeaders(status, 0);
                byte[] initial = new byte[status == 200 ? 65537 : 1];
                Arrays.fill(initial, (byte) ' ');
                exchange.getResponseBody().write(initial);
                exchange.getResponseBody().flush();
                try { releaseTail.await(1500, java.util.concurrent.TimeUnit.MILLISECONDS); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); }
                exchange.getResponseBody().write(' ');
            } catch (java.io.IOException expectedDisconnect) {
                // Rejected response connections should be aborted before the tail is sent.
            } finally { exchange.close(); }
        });
        local.start();
        try {
            java.net.URI loopback = java.net.URI.create("http://127.0.0.1:" + local.getAddress().getPort() + "/completion");
            LlmClient actual = new LlmClient(properties(true, "https://model.example/v1", "max_completion_tokens"),
                    json, new RestTemplateBuilder().additionalCustomizers(template -> {
                        org.springframework.http.client.ClientHttpRequestFactory factory = template.getRequestFactory();
                        // Route only the external endpoint to this local fixture; use the actual production factory.
                        template.setRequestFactory((uri, method) -> factory.createRequest(loopback, method));
                    }));
            assertTimeout(java.time.Duration.ofMillis(900), () -> assertFailure(
                    status == 200 ? "INVALID_RESPONSE" : "RATE_LIMITED",
                    () -> actual.generate("calendar", Collections.singletonList(evidence()))));
        } finally {
            releaseTail.countDown();
            local.stop(0);
        }
    }

    @Test
    void realTransportSendsJsonAndParsesCompleteResponse() throws Exception {
        com.sun.net.httpserver.HttpServer local = com.sun.net.httpserver.HttpServer.create(
                new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        java.util.concurrent.atomic.AtomicReference<String> authorization = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<JsonNode> payload = new java.util.concurrent.atomic.AtomicReference<>();
        local.createContext("/completion", exchange -> {
            try {
                authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                payload.set(json.readTree(exchange.getRequestBody()));
                byte[] answer = completion("Calendar page. [FAQ:7]", "[7]").getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, answer.length);
                exchange.getResponseBody().write(answer);
            } finally { exchange.close(); }
        });
        local.start();
        try {
            java.net.URI loopback = java.net.URI.create("http://127.0.0.1:" + local.getAddress().getPort() + "/completion");
            LlmClient actual = new LlmClient(properties(true, "https://model.example/v1", "max_completion_tokens"),
                    json, new RestTemplateBuilder().additionalCustomizers(template -> {
                        org.springframework.http.client.ClientHttpRequestFactory factory = template.getRequestFactory();
                        template.setRequestFactory((uri, method) -> factory.createRequest(loopback, method));
                    }));
            assertEquals("Calendar page. [FAQ:7]", actual.generate("calendar", Collections.singletonList(evidence())).getAnswer());
            assertEquals("Bearer test-key", authorization.get());
            assertEquals("fixture-model", payload.get().path("model").asText());
            assertEquals("calendar", json.readTree(payload.get().path("messages").get(1).path("content").asText())
                    .path("question").asText());
        } finally { local.stop(0); }
    }

    @Test
    void acceptsMultipleCitationsAndReturnsImmutableIds() throws Exception {
        FaqEntry second = evidence(); second.setId(8L);
        server.expect(requestTo(ENDPOINT)).andRespond(withSuccess(
                completion("Both [FAQ:8] and [FAQ:7].", "[8,7]"), MediaType.APPLICATION_JSON));
        LlmClient.Generation result = client.generate("calendar", Arrays.asList(evidence(), second));
        assertEquals(Arrays.asList(8L, 7L), result.getCitedFaqIds());
        assertThrows(UnsupportedOperationException.class, () -> result.getCitedFaqIds().add(99L));
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "http://model.example/v1", "https://u:test-key@model.example/v1",
            "https://model.example/v1?key=test-key", "https://model.example/v1#test-key",
            "https://model.example/v1/../secret", "https://model.example/v1/%2e%2e", "not a URL"})
    void invalidEnabledConfigurationFailsWithoutPrintingSecrets(String baseUrl) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> properties(true, baseUrl, "max_completion_tokens"));
        assertFalse(error.toString().contains("test-key"));
        assertFalse(error.toString().contains(baseUrl.isEmpty() ? "not present" : baseUrl));
        assertNull(error.getCause());
    }

    @Test
    void requiresKeyModelAndSupportedOutputParameterWhenEnabled() {
        assertThrows(IllegalArgumentException.class, () -> new FaqLlmProperties(true, "https://model.example/v1", "", "m", "max_tokens"));
        assertThrows(IllegalArgumentException.class, () -> new FaqLlmProperties(true, "https://model.example/v1", "k", "", "max_tokens"));
        assertThrows(IllegalArgumentException.class, () -> new FaqLlmProperties(true, "https://model.example/v1", "k\r\nX:y", "m", "max_tokens"));
        assertThrows(IllegalArgumentException.class, () -> properties(true, "https://model.example/v1", "anything"));
    }

    @Test
    void bootDefaultsAreDisabledAndUnderscoredEnvironmentAliasesBind() {
        ApplicationContextRunner context = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class))
                .withUserConfiguration(FaqLlmProperties.class);
        context.run(app -> {
            assertNull(app.getStartupFailure());
            assertFalse(app.getBean(FaqLlmProperties.class).isEnabled());
        });
        context.withInitializer(app -> {
            Map<String, Object> env = new HashMap<>();
            env.put("FAQ_LLM_ENABLED", "true");
            env.put("FAQ_LLM_BASE_URL", "https://model.example/v1");
            env.put("FAQ_LLM_API_KEY", "test-key");
            env.put("FAQ_LLM_MODEL", "fixture-model");
            env.put("FAQ_LLM_OUTPUT_TOKEN_PARAMETER", "max_tokens");
            app.getEnvironment().getPropertySources().addFirst(new SystemEnvironmentPropertySource("faq-test", env));
        }).run(app -> {
            assertNull(app.getStartupFailure());
            FaqLlmProperties props = app.getBean(FaqLlmProperties.class);
            assertTrue(props.isEnabled());
            assertEquals("fixture-model", props.getModel());
            assertEquals("max_tokens", props.getOutputTokenParameter());
        });
        context.withPropertyValues("faq.llm.enabled=true").run(app -> assertNotNull(app.getStartupFailure()));
    }

    private void rejectsResponse(String response) {
        server.expect(requestTo(ENDPOINT)).andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
        assertFailure("INVALID_RESPONSE", this::generate);
        server.verify();
    }

    private void assertFailure(String type, org.junit.jupiter.api.function.Executable action) {
        LlmClient.LlmException error = assertThrows(LlmClient.LlmException.class, action);
        assertEquals(type, error.getType().name());
        assertFalse(error.toString().contains("test-key"));
        assertFalse(error.toString().contains("DO_NOT_SEND"));
        assertNull(error.getCause());
    }

    private LlmClient.Generation generate() {
        return client.generate("Where is the calendar?", Collections.singletonList(evidence()));
    }

    private FaqLlmProperties properties(boolean enabled, String url, String parameter) {
        return new FaqLlmProperties(enabled, url, "test-key", "fixture-model", parameter);
    }

    private FaqEntry evidence() {
        FaqEntry item = new FaqEntry();
        item.setId(7L); item.setQuestion("Calendar?"); item.setAnswer("See the calendar page.");
        item.setEnabled(true); item.setKeywords("DO_NOT_SEND"); item.setCategory("DO_NOT_SEND");
        item.setSourceTitle("DO_NOT_SEND"); item.setSourceUrl("https://DO_NOT_SEND.example");
        return item;
    }

    private String completion(String answer, String ids) {
        try {
            return envelope("{\"answer\":" + json.writeValueAsString(answer) + ",\"citedFaqIds\":" + ids + "}");
        } catch (Exception e) { throw new AssertionError(e); }
    }

    private String envelope(String content) throws Exception {
        return "{\"id\":\"fixture\",\"object\":\"chat.completion\",\"created\":1,\"model\":\"fixture-model\","
                + "\"choices\":[{\"index\":0,\"finish_reason\":\"stop\",\"message\":{\"role\":\"assistant\","
                + "\"content\":" + json.writeValueAsString(content) + ",\"refusal\":null}}],"
                + "\"usage\":{\"prompt_tokens\":40,\"completion_tokens\":20,\"total_tokens\":60}}";
    }

    private static String repeat(String value, int count) {
        return String.join("", Collections.nCopies(count, value));
    }
}
