package com.example.springboot.faq;

import com.example.springboot.faq.config.FaqLlmProperties;
import com.example.springboot.faq.dto.FaqAnswerResponse;
import com.example.springboot.faq.dto.FaqSource;
import com.example.springboot.faq.entity.FaqEntry;
import com.example.springboot.faq.llm.LlmClient;
import com.example.springboot.faq.mapper.FaqMapper;
import com.example.springboot.faq.retrieval.*;
import com.example.springboot.faq.service.FaqService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.net.URI;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.regex.*;

import static com.example.springboot.faq.FaqStep6AcceptanceReportTest.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Manual opt-in only: one pass, SELECT-only MySQL, <=40 DeepSeek and <=45 BGE requests. */
@EnabledIfEnvironmentVariable(named = "FAQ_STEP6_FINAL_LIVE_VERIFY", matches = "true")
@ActiveProfiles({"faq-deepseek", "faq-embedding"})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.main.banner-mode=off", "logging.level.root=WARN",
        "faq.embedding.enabled=true", "faq.deepseek.enabled=true",
        "faq.embedding.semantic-acceptance-enabled=true", "faq.embedding.semantic-threshold=0.73",
        "faq.embedding.candidate-threshold=0.73"})
class FaqStep6AcceptanceIT {
    @Autowired FaqService service;
    @Autowired FaqMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired FaqLlmProperties model;
    @Autowired Environment environment;
    @SpyBean LlmClient llm;
    @SpyBean EmbeddingClient embedding;
    @SpyBean SemanticFaqRetriever semantic;
    private final ObjectNode raw = JSON.createObjectNode();
    private final ObjectNode ledger = JSON.createObjectNode();
    private final Map<Long, String> keys = new HashMap<>();
    private final Map<Long, FaqEntry> databaseRows = new HashMap<>();
    private final Set<String> documentTexts = new HashSet<>();
    private ObjectNode activeCase;
    private Path output;
    private int generations;
    private int embeddings;

    @Test void finalFixedFortyCasesThroughProductionServiceWithoutRetuning() throws Exception {
        output = root().resolve("springboot/target/faq-step6-5-8");
        Files.createDirectories(output);
        assertFalse(Files.exists(output.resolve("live-attempt.json")),
                "A previous attempt exists; replay its responses instead of repeating provider calls");
        assertEquals(HOLDOUT_SHA, sha(root().resolve("docs/faq-evaluation/holdout.json")));
        assertEquals(CORPUS_SHA, sha(root().resolve("database/faq_ccnu_corpus_20261111.json")));
        assertTrue(model.isEnabled());
        assertEquals("https://api.deepseek.com/chat/completions", model.getEndpoint().toString());
        assertEquals("deepseek-flash", model.getModel());
        assertEquals("max_tokens", model.getOutputTokenParameter());
        assertEquals("https://api.siliconflow.cn/v1/embeddings", EmbeddingClient.ENDPOINT);
        assertEquals("BAAI/bge-m3", EmbeddingClient.MODEL);
        assertEquals(1024, EmbeddingClient.DIMENSIONS);
        assertTrue(embedding.isEnabled());
        assertTrue(semantic.isSemanticAcceptanceEnabled());
        assertTrue(environment.getProperty("faq.embedding.semantic-acceptance-enabled", Boolean.class, false));
        assertEquals(0.73, environment.getProperty("faq.embedding.semantic-threshold", Double.class, 0.0).doubleValue());
        try (java.sql.Connection connection = dataSource.getConnection()) {
            URI target = URI.create(connection.getMetaData().getURL().substring(5));
            assertTrue("localhost".equals(target.getHost()) || "127.0.0.1".equals(target.getHost()));
            assertEquals(3306, target.getPort());
        }
        assertEquals("dormitory", jdbc.queryForObject("SELECT DATABASE()", String.class));
        List<Map<String, Object>> before = jdbc.queryForList("SELECT * FROM faq_entry ORDER BY id");
        validateReviewedCorpus(before);

        // Freeze measurement/acceptance policy before opening the fixed set for this run.
        ObjectNode policy = JSON.createObjectNode().put("version", "step6-final-20261118")
                .put("semanticThreshold", 0.73).put("semanticAcceptanceEnabled", true)
                .put("holdoutSha256", HOLDOUT_SHA).put("corpusSha256", CORPUS_SHA)
                .put("maxDeepSeekCalls", 40).put("maxEmbeddingCalls", 45)
                .put("providerRetries", 0).put("queryRewrites", 0)
                .put("minimumPositiveExpectedSourceReturned", 24)
                .put("maximumNegativeAnswered", 0).put("maximumPositiveWrongSource", 0)
                .put("qualityGateRationale", "Preserve fixed-set lexical baseline 24/28 expected-source coverage; zero observed unsupported answers")
                .put("factualSupportRequiresManualReview", true)
                .put("fixedSetIsBlindThirdPartyTest", false);
        save(output.resolve("frozen-policy.json"), policy);
        JsonNode holdout = JSON.readTree(root().resolve("docs/faq-evaluation/holdout.json").toFile()).path("cases");
        assertEquals(40, holdout.size());
        raw.put("version", "step6-final-20261118").put("startedAt", Instant.now().toString())
                .put("holdoutSha256", HOLDOUT_SHA).put("corpusSha256", CORPUS_SHA)
                .put("expectedCaseCount", 40).put("completed", false).put("databaseUnchanged", false);
        raw.set("policy", policy);
        raw.put("generationModel", model.getModel()).put("generationEndpoint", model.getEndpoint().toString())
                .put("embeddingModel", EmbeddingClient.MODEL).put("embeddingEndpoint", EmbeddingClient.ENDPOINT);
        ArrayNode rows = raw.putArray("cases");
        ledger.put("startedAt", Instant.now().toString()).put("deepSeekAttempts", 0).put("embeddingAttempts", 0);
        Files.write(output.resolve("live-attempt.json"), JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(ledger),
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        installBudgetGuards();
        try {
            for (JsonNode item : holdout) {
                activeCase = rows.addObject();
                activeCase.put("id", item.path("id").asText()).put("question", item.path("question").asText())
                        .put("positive", item.path("expectedKeys").size() > 0).put("kind", item.path("kind").asText())
                        .put("providerFailed", false).put("retrievalAvailable", false);
                activeCase.set("expectedKeys", item.path("expectedKeys").deepCopy());
                long started = System.nanoTime();
                try {
                    FaqAnswerResponse answer = service.ask(item.path("question").asText());
                    recordAnswer(item, answer);
                } catch (RuntimeException failure) {
                    // Exception messages/bodies may contain upstream data: persist only the class name.
                    activeCase.put("mode", "unavailable").put("exceptionType", failure.getClass().getSimpleName());
                } finally {
                    activeCase.put("elapsedMillis", (System.nanoTime() - started) / 1000000);
                    persist();
                }
                // Continuing after an embedding outage would measure cooldown/fallback, not this policy.
                if (!activeCase.path("retrievalAvailable").asBoolean()) break;
            }
            raw.put("completed", rows.size() == 40);
            verify(llm, never()).resolve(anyString(), anyList(), any());
        } finally {
            raw.put("databaseUnchanged", before.equals(jdbc.queryForList("SELECT * FROM faq_entry ORDER BY id")));
            raw.put("finishedAt", Instant.now().toString());
            persist();
            save(root().resolve("docs/faq-evaluation/step6-final-20261118.json"), summarize(raw));
        }
        assertTrue(raw.path("databaseUnchanged").asBoolean(), "Read-only evaluation must leave all FAQ fields unchanged");
        assertEquals(40, rows.size(), "Provider/index failure left a partial measurement; do not repeat live automatically");
        assertTrue(generations <= 40 && embeddings <= 45);
        // Report quality-gate failure as a measured result; never tune a threshold to make this test pass.
    }

    private void validateReviewedCorpus(List<Map<String, Object>> before) throws Exception {
        JsonNode corpus = JSON.readTree(root().resolve("database/faq_ccnu_corpus_20261111.json").toFile());
        Map<String, JsonNode> reviewed = new HashMap<>();
        for (JsonNode row : corpus.path("entries")) assertNull(reviewed.put(row.path("question").asText(), row));
        List<FaqEntry> entries = mapper.findEnabled();
        assertEquals(40, before.size()); assertEquals(40, entries.size()); assertEquals(40, reviewed.size());
        for (FaqEntry row : entries) {
            JsonNode expected = reviewed.get(row.getQuestion());
            assertNotNull(expected, "Only reviewed public FAQ evidence may be sent");
            JsonNode source = corpus.path("sources").path(expected.path("sourceKey").asText());
            assertEquals(expected.path("answer").asText(), row.getAnswer());
            assertEquals(expected.path("keywords").asText(), row.getKeywords());
            assertEquals(expected.path("category").asText(), row.getCategory());
            assertEquals(source.path("url").asText(), row.getSourceUrl());
            assertEquals(expected.path("sourceTitle").asText(), row.getSourceTitle());
            assertEquals(Boolean.TRUE, row.getEnabled());
            assertNull(keys.put(row.getId(), expected.path("key").asText()));
            assertFalse(keys.get(row.getId()).isEmpty());
            databaseRows.put(row.getId(), row);
            documentTexts.add(row.getQuestion() + " " + row.getKeywords());
        }
    }

    private void installBudgetGuards() {
        doAnswer(call -> {
            List<String> texts = call.getArgument(0);
            for (String text : texts) assertTrue(documentTexts.contains(text)
                    || (texts.size() == 1 && text.equals(activeCase.path("question").asText())), "Unexpected outbound text");
            if (embeddings >= 45) throw new AssertionError("Embedding budget exhausted before outbound call");
            embeddings++;
            ledger.put("embeddingAttempts", embeddings); save(output.resolve("live-attempt.json"), ledger);
            try { return call.callRealMethod(); }
            catch (RuntimeException failure) { activeCase.put("providerFailed", true); throw failure; }
        }).when(embedding).embed(anyList(), any());
        doAnswer(call -> {
            assertEquals(activeCase.path("question").asText(), call.getArgument(0));
            if (generations >= 40) throw new AssertionError("DeepSeek budget exhausted before outbound call");
            generations++;
            ledger.put("deepSeekAttempts", generations); save(output.resolve("live-attempt.json"), ledger);
            List<FaqEntry> contexts = call.getArgument(1);
            ArrayNode contextKeys = activeCase.putArray("generationContextKeys");
            for (FaqEntry row : contexts) {
                assertEquals(databaseRows.get(row.getId()).getAnswer(), row.getAnswer());
                contextKeys.add(keys.get(row.getId()));
            }
            try { return call.callRealMethod(); }
            catch (RuntimeException failure) { activeCase.put("providerFailed", true); throw failure; }
        }).when(llm).generate(anyString(), anyList(), any());
        doAnswer(call -> { throw new AssertionError("Fixed /ask evaluation forbids query rewrites"); })
                .when(llm).resolve(anyString(), anyList(), any());
        doAnswer(call -> {
            SemanticFaqRetriever.Result result = (SemanticFaqRetriever.Result) call.callRealMethod();
            activeCase.put("retrievalAvailable", result.isAvailable());
            ArrayNode retrieved = activeCase.putArray("retrievedKeys");
            for (TfIdfRetriever.Match match : result.getMatches()) retrieved.add(keys.get(match.getEntry().getId()));
            return result;
        }).when(semantic).search(anyString(), anyList(), any());
    }

    private void recordAnswer(JsonNode item, FaqAnswerResponse answer) {
        activeCase.set("response", JSON.valueToTree(answer));
        activeCase.put("mode", answer.getMode()).put("matched", answer.isMatched()).put("answer", answer.getAnswer());
        ArrayNode sourceKeys = activeCase.putArray("sourceKeys");
        Set<Long> sourceIds = new HashSet<>();
        boolean valid = true;
        for (FaqSource source : answer.getSources()) {
            FaqEntry stored = databaseRows.get(source.getFaqId());
            valid &= stored != null && Objects.equals(stored.getSourceUrl(), source.getSourceUrl())
                    && Objects.equals(stored.getSourceTitle(), source.getSourceTitle()) && sourceIds.add(source.getFaqId());
            sourceKeys.add(keys.get(source.getFaqId()));
        }
        if ("generated".equals(answer.getMode())) {
            Set<Long> markers = new HashSet<>();
            Matcher matcher = Pattern.compile("\\[FAQ:([1-9][0-9]*)\\]").matcher(answer.getAnswer());
            while (matcher.find()) markers.add(Long.parseLong(matcher.group(1)));
            valid &= !markers.isEmpty() && markers.equals(sourceIds);
        }
        if ("no_match".equals(answer.getMode())) valid &= sourceIds.isEmpty() && !answer.isMatched();
        boolean expectedReturned = false;
        for (JsonNode key : item.path("expectedKeys")) for (JsonNode actual : sourceKeys) expectedReturned |= key.equals(actual);
        activeCase.put("expectedSourceReturned", expectedReturned).put("citationMetadataValid", valid);
        ArrayNode missing = activeCase.putArray("missingLiteralFacts");
        for (JsonNode fact : item.path("mustPreserveFacts")) {
            String searchable = Objects.toString(answer.getAnswer(), "") + " " + answer.getSources();
            if (!searchable.toLowerCase(Locale.ROOT).contains(fact.asText().toLowerCase(Locale.ROOT))) missing.add(fact.asText());
        }
        activeCase.put("literalFactChecksAreFactualProof", false);
    }

    private void persist() throws Exception {
        raw.put("deepSeekAttempts", generations).put("embeddingAttempts", embeddings);
        save(output.resolve("responses.json"), raw);
    }

    private static void save(Path path, JsonNode value) throws Exception {
        Files.createDirectories(path.getParent());
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        Files.write(temporary, JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(value));
        Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
    }
}
