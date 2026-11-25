package com.example.springboot.faq;

import com.example.springboot.faq.config.FaqEmbeddingProperties;
import com.example.springboot.faq.entity.FaqEntry;
import com.example.springboot.faq.retrieval.*;
import com.example.springboot.faq.support.FaqRequestBudget;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit calibration only. No Spring/DB/chat model; live and frozen-score replay have separate gates. */
class FaqThresholdCalibrationIT {
    private static final ObjectMapper JSON = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    private final Path root = Paths.get(System.getProperty("basedir", ".")).toAbsolutePath().getParent();
    private final Path output = root.resolve("springboot/target/faq-step6-1-4");
    private final Path matrixFile = root.resolve("docs/faq-evaluation/step6-scores-20261117.json");
    private final Path summaryFile = root.resolve("docs/faq-evaluation/step6-thresholds-20261117.json");

    @Test @EnabledIfEnvironmentVariable(named = "FAQ_STEP6_LIVE_VERIFY", matches = "true")
    void measureFreshPublicCorpusAndCalibrationAgainstAllFortyEntries() throws Exception {
        Inputs input = inputs();
        List<List<String>> batches = EmbeddingSelectionProbe.batches(input.texts);
        int inputBytes = input.texts.stream().mapToInt(t -> t.getBytes(StandardCharsets.UTF_8).length).sum();
        assertEquals(92, input.texts.size()); assertTrue(batches.size() <= 8 && inputBytes <= 16000);
        assertFalse(Files.exists(matrixFile), "A completed run already exists; replay it without new provider calls.");
        Files.createDirectories(output);
        Path ledgerFile = output.resolve("live-attempts.json");
        assertFalse(Files.exists(ledgerFile), "A live attempt already exists; any further calls need a new explicit scope.");
        EmbeddingClient client = new EmbeddingClient(new FaqEmbeddingProperties(true, System.getenv("SILICONFLOW_API_KEY")), JSON);
        ObjectNode ledger = JSON.createObjectNode().put("date", "2026-11-17").put("model", EmbeddingClient.MODEL)
                .put("maxRequests", 8).put("requestsAttempted", 0).put("inputTexts", input.texts.size())
                .put("inputTextBytes", inputBytes).put("databaseAccess", false).put("deepseekCalls", 0)
                .put("inputSha256", ThresholdCalibration.sha256(JSON.writeValueAsBytes(input.texts))).put("completed", false);
        List<double[]> vectors = new ArrayList<>();
        for (List<String> batch : batches) {
            int attempts = ledger.path("requestsAttempted").intValue() + 1;
            assertTrue(attempts <= 8);
            ledger.put("requestsAttempted", attempts); write(ledgerFile, ledger);
            vectors.addAll(Arrays.asList(client.embed(batch, new FaqRequestBudget(35000))));
        }
        assertEquals(input.texts.size(), vectors.size());
        for (double[] v : vectors) assertEquals(1024, v.length);
        Path vectorsFile = output.resolve("normalized-vectors.json"); write(vectorsFile, JSON.valueToTree(vectors));
        ledger.put("vectorsSha256", ThresholdCalibration.sha256(Files.readAllBytes(vectorsFile))); write(ledgerFile, ledger);
        finish(input, vectors, ledger, ledgerFile);
    }

    @Test @EnabledIfEnvironmentVariable(named = "FAQ_STEP6_RECOVER_VERIFY", matches = "true")
    void finishSavedCompleteVectorsOfflineWithoutFurtherProviderCalls() throws Exception {
        Inputs input = inputs(); Path ledgerFile = output.resolve("live-attempts.json");
        ObjectNode ledger = (ObjectNode) JSON.readTree(ledgerFile.toFile());
        assertFalse(ledger.path("completed").asBoolean()); assertFalse(Files.exists(matrixFile));
        assertEquals(EmbeddingClient.MODEL, ledger.path("model").asText());
        assertEquals(ThresholdCalibration.sha256(bytes(input.texts)), ledger.path("inputSha256").asText());
        assertEquals(EmbeddingSelectionProbe.batches(input.texts).size(), ledger.path("requestsAttempted").intValue());
        Path vectorsFile = output.resolve("normalized-vectors.json");
        String savedHash = ThresholdCalibration.sha256(Files.readAllBytes(vectorsFile));
        if (ledger.has("vectorsSha256")) assertEquals(savedHash, ledger.path("vectorsSha256").asText());
        double[][] vectors = JSON.readValue(vectorsFile.toFile(), double[][].class);
        assertEquals(input.texts.size(), vectors.length);
        for (double[] vector : vectors) { assertEquals(1024, vector.length); ThresholdCalibration.cosine(vector, vector); }
        ledger.put("vectorsSha256", savedHash).put("recoveredOfflineAfterReportBug", true);
        finish(input, Arrays.asList(vectors), ledger, ledgerFile);
    }

    private void finish(Inputs input, List<double[]> vectors, ObjectNode ledger, Path ledgerFile) throws Exception {
        ObjectNode matrix = metadata(input).put("vectorsSha256", ledger.path("vectorsSha256").asText())
                .put("requestsAttempted", ledger.path("requestsAttempted").intValue()).put("inputTextBytes", ledger.path("inputTextBytes").intValue());
        ArrayNode cases = matrix.putArray("cases"); int query = input.entries.size();
        for (JsonNode row : input.cases) {
            ObjectNode item = cases.addObject().put("id", row.path("id").asText());
            ArrayNode ranked = item.putArray("ranked");
            if ("clarify".equals(row.path("outcome").asText())) continue;
            for (ThresholdCalibration.Hit hit : ThresholdCalibration.rank(input.keys, vectors.subList(0, input.entries.size()), vectors.get(query++)))
                ranked.addObject().put("key", hit.key).put("score", hit.score);
        }
        assertEquals(vectors.size(), query);
        ObjectNode summary = analyze(input, matrix);
        write(matrixFile, matrix); write(summaryFile, summary);
        ledger.put("completed", true).put("matrixSha256", ThresholdCalibration.sha256(Files.readAllBytes(matrixFile)));
        write(ledgerFile, ledger);
    }

    @Test @EnabledIfEnvironmentVariable(named = "FAQ_STEP6_REPLAY_VERIFY", matches = "true")
    void replayFrozenScoresProducesExactlyTheSavedThresholdReportWithoutNetwork() throws Exception {
        Inputs input = inputs(); JsonNode matrix = JSON.readTree(matrixFile.toFile());
        assertEquals(JSON.readTree(summaryFile.toFile()), analyze(input, matrix));
        write(output.resolve("replay-result.json"), JSON.createObjectNode().put("passed", true).put("providerCalls", 0)
                .put("matrixSha256", ThresholdCalibration.sha256(Files.readAllBytes(matrixFile))));
    }

    private Inputs inputs() throws Exception {
        Path corpusFile = root.resolve("database/faq_ccnu_corpus_20261111.json");
        Path calibrationFile = root.resolve("docs/faq-evaluation/calibration-step6-20261117.json");
        JsonNode corpus = JSON.readTree(corpusFile.toFile()), data = JSON.readTree(calibrationFile.toFile());
        Inputs input = new Inputs(); input.corpusHash = ThresholdCalibration.sha256(Files.readAllBytes(corpusFile));
        input.calibrationHash = ThresholdCalibration.sha256(Files.readAllBytes(calibrationFile));
        assertEquals("3fcf509d3235eb69df47d6f9709b51b254437c78654218f8262c8e21e09ba3ae", input.calibrationHash,
                "Calibration labels were frozen before the first provider call.");
        input.holdoutHash = ThresholdCalibration.sha256(Files.readAllBytes(root.resolve("docs/faq-evaluation/holdout.json")));
        assertEquals("55808b345d4c61fe2aedd4ba5ae89454818f3ebe5498912bbc8e0244c7f1cc8a", input.corpusHash);
        Set<String> questions = new HashSet<>();
        for (JsonNode row : corpus.path("entries")) {
            assertTrue(row.path("enabled").asBoolean());
            JsonNode source = corpus.path("sources").path(row.path("sourceKey").asText());
            String host = URI.create(source.path("url").asText()).getHost();
            assertTrue("ccnu.edu.cn".equals(host) || host.endsWith(".ccnu.edu.cn"));
            FaqEntry entry = new FaqEntry(); entry.setId((long) input.entries.size() + 1);
            entry.setQuestion(row.path("question").asText()); entry.setKeywords(row.path("keywords").asText());
            assertFalse(entry.getQuestion().trim().isEmpty());
            assertTrue(questions.add(entry.getQuestion()));
            input.entries.add(entry); input.keys.add(row.path("key").asText());
            input.texts.add(entry.getQuestion() + " " + entry.getKeywords());
        }
        assertEquals(40, input.entries.size()); assertEquals(40, new HashSet<>(input.keys).size());
        Set<String> ids = new HashSet<>();
        for (JsonNode row : data.path("cases")) {
            assertTrue(ids.add(row.path("id").asText()));
            Set<String> expected = expected(row);
            assertTrue(input.keys.containsAll(expected));
            String outcome = row.path("outcome").asText();
            assertTrue(Arrays.asList("answerable", "no_match", "clarify").contains(outcome));
            assertEquals("answerable".equals(outcome), !expected.isEmpty());
            input.cases.add(row);
            if (!"clarify".equals(outcome)) input.texts.add(row.path("question").asText());
        }
        assertEquals(60, input.cases.size());
        return input;
    }

    private ObjectNode metadata(Inputs input) {
        return JSON.createObjectNode().put("version", "2026-11-17-step6-parts1-4").put("model", EmbeddingClient.MODEL)
                .put("dimensions", 1024).put("representation", "question + space + keywords")
                .put("corpusSha256", input.corpusHash).put("calibrationSha256", input.calibrationHash)
                .put("holdoutSha256", input.holdoutHash).put("inputSha256", ThresholdCalibration.sha256(bytes(input.texts)))
                .put("holdoutScoresComputed", false).put("productionChanged", false);
    }

    private ObjectNode analyze(Inputs input, JsonNode matrix) {
        ObjectNode required = metadata(input);
        required.fields().forEachRemaining(field -> assertEquals(field.getValue(), matrix.get(field.getKey()), "Mismatched frozen input/provenance"));
        assertTrue(matrix.path("vectorsSha256").asText().matches("[a-f0-9]{64}"));
        assertEquals(60, matrix.path("cases").size());
        Map<String, JsonNode> scored = new HashMap<>();
        for (JsonNode row : matrix.path("cases")) assertNull(scored.put(row.path("id").asText(), row));
        List<ThresholdCalibration.ScoredCase> semantic = new ArrayList<>(), lexical = new ArrayList<>(), step5 = new ArrayList<>();
        TfIdfRetriever retriever = new TfIdfRetriever();
        for (JsonNode row : input.cases) {
            String id = row.path("id").asText(), outcome = row.path("outcome").asText(); JsonNode frozen = scored.get(id); assertNotNull(frozen);
            List<ThresholdCalibration.Hit> hits = new ArrayList<>();
            for (JsonNode hit : frozen.path("ranked")) {
                assertTrue(hit.path("score").isNumber()); hits.add(new ThresholdCalibration.Hit(hit.path("key").asText(), hit.path("score").doubleValue()));
            }
            if ("clarify".equals(outcome)) { assertTrue(hits.isEmpty()); continue; }
            assertEquals(40, hits.size());
            assertEquals(new HashSet<>(input.keys), hits.stream().map(h -> h.key).collect(Collectors.toSet()));
            semantic.add(new ThresholdCalibration.ScoredCase(id, outcome, expected(row), hits));
            Map<String, Double> lexicalScores = new HashMap<>();
            for (TfIdfRetriever.Match hit : retriever.findTopMatches(row.path("question").asText(), input.entries, 40))
                lexicalScores.put(input.keys.get(hit.getEntry().getId().intValue() - 1), hit.getScore());
            List<ThresholdCalibration.Hit> lexicalHits = new ArrayList<>();
            for (ThresholdCalibration.Hit hit : hits) {
                double score = lexicalScores.getOrDefault(hit.key, 0.0);
                lexicalHits.add(new ThresholdCalibration.Hit(hit.key, score));
            }
            sort(lexicalHits);
            List<ThresholdCalibration.Hit> gated = ThresholdCalibration.step5Ranking(hits, lexicalScores);
            lexical.add(new ThresholdCalibration.ScoredCase(id, outcome, expected(row), lexicalHits));
            step5.add(new ThresholdCalibration.ScoredCase(id, outcome, expected(row), gated));
        }
        assertEquals(52, semantic.size());
        List<ThresholdCalibration.Row> sweep = ThresholdCalibration.sweep(semantic);
        ThresholdCalibration.Row selected = ThresholdCalibration.select(sweep);
        ObjectNode report = metadata(input).put("scoredQueries", 52).put("excludedClarifications", 8).put("scoresPerQuery", 40)
                .put("requestsAttempted", matrix.path("requestsAttempted").intValue()).put("gridStep", .01).put("readyForProduction", false)
                .put("selectionRule", "zero negativeAccepted and wrongTop1Accepted; maximize correctTop1Accepted; tie lowest threshold");
        report.set("lexicalAt035", JSON.valueToTree(ThresholdCalibration.measure(lexical, .35)));
        report.set("step5LexicalGateBgeOrder", JSON.valueToTree(ThresholdCalibration.measure(step5, 0)));
        report.set("unthresholdedBge", JSON.valueToTree(ThresholdCalibration.measure(semantic, 0)));
        report.set("sweep", JSON.valueToTree(sweep));
        report.set("selected", selected == null ? NullNode.instance : JSON.valueToTree(selected));
        report.put("selectionStatus", selected == null ? "no_feasible_threshold" : selected.metrics.correctTop1Accepted == 0 ? "all_reject_only" : "offline_candidate_only");
        report.put("retainsLexicalCorrectTop1", selected != null && selected.metrics.correctTop1Accepted >= ThresholdCalibration.measure(lexical, .35).correctTop1Accepted);
        return report;
    }

    private void sort(List<ThresholdCalibration.Hit> hits) { hits.sort(Comparator.comparingDouble((ThresholdCalibration.Hit h) -> h.score).reversed().thenComparing(h -> h.key)); }
    private Set<String> expected(JsonNode row) { Set<String> keys = new HashSet<>(); for (JsonNode key : row.path("expectedKeys")) assertTrue(keys.add(key.asText())); return keys; }
    private byte[] bytes(Object value) { try { return JSON.writeValueAsBytes(value); } catch (java.io.IOException error) { throw new IllegalArgumentException(error); } }
    private void write(Path file, JsonNode value) throws Exception { Files.createDirectories(file.getParent()); JSON.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), value); }
    private static final class Inputs {
        String corpusHash, calibrationHash, holdoutHash;
        final List<FaqEntry> entries = new ArrayList<>(); final List<String> keys = new ArrayList<>(), texts = new ArrayList<>();
        final List<JsonNode> cases = new ArrayList<>();
    }
}
