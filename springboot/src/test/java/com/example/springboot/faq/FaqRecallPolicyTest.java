package com.example.springboot.faq;

import com.example.springboot.faq.config.FaqLlmProperties;
import com.example.springboot.faq.dto.FaqAnswerResponse;
import com.example.springboot.faq.entity.FaqEntry;
import com.example.springboot.faq.llm.LlmClient;
import com.example.springboot.faq.mapper.FaqMapper;
import com.example.springboot.faq.retrieval.*;
import com.example.springboot.faq.service.FaqRequestRunner;
import com.example.springboot.faq.service.FaqService;
import com.example.springboot.faq.support.FaqRequestBudget;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/** Real service/retriever/client; only DB, embedding provider and completion HTTP are controlled. */
class FaqRecallPolicyTest {
    private final ObjectMapper json = new ObjectMapper();
    private final EmbeddingClient embedding = mock(EmbeddingClient.class);
    private final FaqMapper mapper = mock(FaqMapper.class);
    private MockRestServiceServer model;
    private static final String QUESTION = "Recover access to central sign-in";
    private static final String ANSWER = "Use the published recovery instructions. [FAQ:1]";
    private static final String REFUSAL = "{\"action\":\"insufficient_evidence\",\"answer\":\"\",\"citedFaqIds\":[]}";

    private LlmClient client(boolean enabled) {
        return new LlmClient(new FaqLlmProperties(enabled, "https://fixture.example/v1", "fixture-key",
                "fixture-model", "max_tokens"), json, new RestTemplateBuilder().additionalCustomizers(
                template -> model = MockRestServiceServer.bindTo(template).build()));
    }

    private FaqEntry entry(long id) {
        FaqEntry entry = new FaqEntry();
        entry.setId(id); entry.setQuestion("identity recovery"); entry.setKeywords("password");
        entry.setAnswer("Use the published recovery instructions."); entry.setEnabled(true);
        entry.setSourceTitle("Identity guide"); entry.setSourceUrl("https://nisc.ccnu.edu.cn/identity");
        return entry;
    }

    private SemanticFaqRetriever retriever(double score, double candidateFloor) {
        when(embedding.isEnabled()).thenReturn(true);
        when(embedding.embed(anyList(), any())).thenAnswer(call -> {
            List<String> texts = call.getArgument(0);
            double[][] result = new double[texts.size()][];
            for (int i = 0; i < texts.size(); i++) {
                double s = texts.get(i).equals("identity recovery password") ? 1 : score;
                result[i] = new double[1024]; result[i][0] = s;
                result[i][1] = Math.sqrt(1 - s * s);
            }
            return result;
        });
        when(mapper.findEnabled()).thenReturn(Collections.singletonList(entry(1)));
        return new SemanticFaqRetriever(embedding, true, .73, candidateFloor);
    }

    private FaqService service(SemanticFaqRetriever retrieval, LlmClient llm) {
        return new FaqService(mapper, new TfIdfRetriever(), .35, llm, retrieval);
    }

    private void completion(String content) {
        ObjectNode body = json.createObjectNode();
        body.putArray("choices").addObject().put("finish_reason", "stop")
                .putObject("message").put("role", "assistant").put("content", content);
        model.expect(requestTo("https://fixture.example/v1/chat/completions"))
                .andExpect(request -> {
                    com.fasterxml.jackson.databind.JsonNode input = json.readTree(
                            json.readTree(request.getBody().toString()).path("messages").get(1).path("content").asText());
                    assertEquals(1, input.path("contexts").size());
                    assertEquals(1, input.path("contexts").get(0).path("id").asInt());
                    assertFalse(input.toString().contains("sourceUrl"));
                }).andRespond(withSuccess(body.toString(), MediaType.APPLICATION_JSON));
    }

    @Test void lowerScoreCanAnswerOnlyAfterValidatedGeneration() {
        FaqService subject = service(retriever(.65, .60), client(true));
        completion("{\"answer\":\"" + ANSWER + "\",\"citedFaqIds\":[1]}");
        FaqAnswerResponse result = subject.ask(QUESTION);
        assertEquals("generated", result.getMode()); assertEquals(ANSWER, result.getAnswer());
        assertEquals(1L, result.getSources().get(0).getFaqId()); model.verify();
    }

    @Test void lowerScoreIsNotAStoredAnswerWhenGenerationIsDisabled() {
        FaqService subject = service(retriever(.65, .60), client(false));
        assertEquals("no_match", subject.ask(QUESTION).getMode());
        assertTrue(subject.retrieveContexts(QUESTION).isEmpty()); model.verify();
    }

    @Test void originalThresholdStillPermitsStoredAnswerWithGenerationDisabled() {
        FaqService subject = service(retriever(.73, .60), client(false));
        assertEquals("extractive", subject.ask(QUESTION).getMode()); model.verify();
    }

    @Test void healthyModelRefusalClearsCandidateAndFallback() {
        FaqService subject = service(retriever(.65, .60), client(true)); completion(REFUSAL);
        FaqRequestBudget budget = new FaqRequestBudget(35000);
        FaqAnswerResponse result = subject.ask(QUESTION, budget);
        assertEquals("no_match", result.getMode()); assertNull(result.getAnswer());
        assertTrue(result.getSources().isEmpty()); assertEquals("no_match", budget.getFallback().getMode());
        model.verify();
    }

    @ParameterizedTest @ValueSource(strings = {"{\"answer\":\"invented [FAQ:999]\",\"citedFaqIds\":[999]}",
            "{\"answer\":\"Visit https://invented.example [FAQ:1]\",\"citedFaqIds\":[1]}", "not-json"})
    void invalidGenerationNeverBecomesLowScoreExcerpt(String invalid) {
        FaqService subject = service(retriever(.65, .60), client(true)); completion(invalid);
        assertEquals("no_match", subject.ask(QUESTION).getMode()); model.verify();
    }

    @Test void upstreamErrorNeverBecomesLowScoreExcerpt() {
        FaqService subject = service(retriever(.65, .60), client(true));
        model.expect(anything()).andRespond(withServerError());
        assertEquals("no_match", subject.ask(QUESTION).getMode()); model.verify();
    }

    @Test void belowCandidateFloorNeverCallsGeneration() {
        FaqService subject = service(retriever(.599, .60), client(true));
        assertEquals("no_match", subject.ask(QUESTION).getMode()); model.verify();
    }

    @Test void candidateBoundaryIsInclusiveButStrictSearchKeepsOriginalThreshold() {
        SemanticFaqRetriever subject = retriever(.60, .60);
        assertEquals(1, subject.searchCandidates(QUESTION, mapper.findEnabled(), new FaqRequestBudget(35000)).getMatches().size());
        assertTrue(subject.search(QUESTION, mapper.findEnabled(), new FaqRequestBudget(35000)).getMatches().isEmpty());
    }

    @Test void oldConstructorDefaultsToOriginalAdmissionPolicy() {
        retriever(.65, .60);
        SemanticFaqRetriever subject = new SemanticFaqRetriever(embedding, true, .73);
        assertTrue(subject.searchCandidates(QUESTION, mapper.findEnabled(), new FaqRequestBudget(35000)).getMatches().isEmpty());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void springUsesSemanticThresholdAsDefaultAndAllowsExplicitCandidateFloor(boolean explicit) {
        retriever(.70, .60);
        ApplicationContextRunner context = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class))
                .withUserConfiguration(SemanticFaqRetriever.class).withBean(EmbeddingClient.class, () -> embedding)
                .withPropertyValues("faq.embedding.semantic-acceptance-enabled=true", "faq.embedding.semantic-threshold=0.81");
        if (explicit) context = context.withPropertyValues("faq.embedding.candidate-threshold=0.60");
        context.run(app -> {
            assertNull(app.getStartupFailure());
            SemanticFaqRetriever subject = app.getBean(SemanticFaqRetriever.class);
            assertEquals(explicit ? 1 : 0, subject.searchCandidates(QUESTION, mapper.findEnabled(),
                    new FaqRequestBudget(35000)).getMatches().size());
            assertTrue(subject.search(QUESTION, mapper.findEnabled(), new FaqRequestBudget(35000)).getMatches().isEmpty());
        });
    }

    @ParameterizedTest @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, 0, -.1, .74, 1.1})
    void rejectsInvalidOrInvertedCandidateThresholds(double threshold) {
        assertThrows(IllegalArgumentException.class, () -> new SemanticFaqRetriever(embedding, true, .73, threshold));
    }

    @Test void candidateRankingIsStableAndCappedAtThree() {
        SemanticFaqRetriever subject = retriever(.65, .60);
        List<FaqEntry> rows = Arrays.asList(entry(9), entry(3), entry(5), entry(2));
        assertEquals(Arrays.asList(2L, 3L, 5L), subject.searchCandidates(QUESTION, rows,
                new FaqRequestBudget(35000)).getMatches().stream().map(m -> m.getEntry().getId()).collect(Collectors.toList()));
    }

    @Test void embeddingFailureCanUseLexicalContextForGeneration() {
        FaqService subject = service(retriever(.65, .60), client(true));
        when(embedding.embed(anyList(), any())).thenThrow(new IllegalArgumentException("unavailable"));
        completion("{\"answer\":\"" + ANSWER + "\",\"citedFaqIds\":[1]}");
        assertEquals("generated", subject.ask("identity recovery").getMode()); model.verify();
    }

    @Test void embeddingFailureCannotPublishLexicalExcerptDuringGenerationDeadline() throws Exception {
        SemanticFaqRetriever retrieval = retriever(.65, .60);
        when(embedding.embed(anyList(), any())).thenThrow(new IllegalArgumentException("unavailable"));
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        LlmClient blocking = new LlmClient(new FaqLlmProperties(false, "", "", "", "max_tokens"), json, new RestTemplateBuilder()) {
            private int checks;
            @Override public boolean isEnabled() {
                // Reproduce the old fallback window: retrieval published the excerpt before this check.
                if (++checks == 1) { entered.countDown(); await(release); }
                return true;
            }
            @Override public Generation generate(String q, List<FaqEntry> rows, FaqRequestBudget budget) {
                await(release); budget.checkActive(); throw new AssertionError("Deadline must have expired");
            }
        };
        FaqService subject = service(retrieval, blocking);
        FaqRequestRunner runner = new FaqRequestRunner(subject, 250);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            Future<FaqAnswerResponse> response = worker.submit(() -> runner.ask("identity recovery"));
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            assertEquals("no_match", response.get(2, TimeUnit.SECONDS).getMode());
        } finally { release.countDown(); runner.close(); worker.shutdownNow(); }
    }

    @Test void databaseFailureIsNotConvertedToRefusal() {
        FaqService subject = service(retriever(.65, .60), client(true));
        when(mapper.findEnabled()).thenThrow(new DataAccessResourceFailureException("private"));
        assertThrows(DataAccessResourceFailureException.class, () -> subject.ask(QUESTION)); model.verify();
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void databaseDeadlineIsUnavailableRatherThanHealthyRefusal(boolean generationEnabled) throws Exception {
        FaqService subject = service(retriever(.65, .60), client(generationEnabled));
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        when(mapper.findEnabled()).thenAnswer(call -> {
            entered.countDown(); await(release); return Collections.singletonList(entry(1));
        });
        FaqRequestRunner runner = new FaqRequestRunner(subject, 350);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            Future<FaqAnswerResponse> result = worker.submit(() -> runner.ask(QUESTION));
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            ExecutionException unavailable = assertThrows(ExecutionException.class, () -> result.get(2, TimeUnit.SECONDS));
            assertTrue(unavailable.getCause() instanceof DataAccessResourceFailureException);
            model.verify();
        } finally { release.countDown(); runner.close(); worker.shutdownNow(); }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void generationDeadlineKeepsNoAnswerForBothCandidatesAndLexicalOutageFallback(boolean embeddingFailed) throws Exception {
        SemanticFaqRetriever retrieval = retriever(.65, .60);
        if (embeddingFailed) when(embedding.embed(anyList(), any())).thenThrow(new IllegalArgumentException("unavailable"));
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        LlmClient blocking = new LlmClient(new FaqLlmProperties(false, "", "", "", "max_tokens"), json, new RestTemplateBuilder()) {
            @Override public boolean isEnabled() { return true; }
            @Override public Generation generate(String q, List<FaqEntry> rows, FaqRequestBudget budget) {
                entered.countDown(); await(release); budget.checkActive(); throw new AssertionError("Deadline must have expired");
            }
        };
        FaqRequestRunner runner = new FaqRequestRunner(service(retrieval, blocking), 500);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            Future<FaqAnswerResponse> response = worker.submit(() -> runner.ask("identity recovery"));
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            FaqAnswerResponse answer = response.get(2, TimeUnit.SECONDS);
            assertEquals("no_match", answer.getMode()); assertNull(answer.getAnswer()); assertTrue(answer.getSources().isEmpty());
        } finally { release.countDown(); runner.close(); worker.shutdownNow(); }
    }

    @Test void legacyModeStillPublishesItsStoredAnswerBeforeCheckingGeneration() {
        retriever(1, .60);
        LlmClient blocking = new LlmClient(new FaqLlmProperties(false, "", "", "", "max_tokens"), json, new RestTemplateBuilder()) {
            @Override public boolean isEnabled() { await(new CountDownLatch(1)); return true; }
        };
        FaqRequestRunner runner = new FaqRequestRunner(service(new SemanticFaqRetriever(embedding), blocking), 250);
        try { assertEquals("extractive", runner.ask("identity recovery").getMode()); }
        finally { runner.close(); }
    }

    private static void await(CountDownLatch latch) {
        try { latch.await(2, TimeUnit.SECONDS); } catch (InterruptedException expected) { Thread.currentThread().interrupt(); }
    }
}
