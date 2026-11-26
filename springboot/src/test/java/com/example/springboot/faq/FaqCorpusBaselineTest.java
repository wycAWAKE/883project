package com.example.springboot.faq;

import com.example.springboot.faq.config.FaqLlmProperties;
import com.example.springboot.faq.dto.FaqAnswerResponse;
import com.example.springboot.faq.entity.FaqEntry;
import com.example.springboot.faq.llm.LlmClient;
import com.example.springboot.faq.mapper.FaqMapper;
import com.example.springboot.faq.retrieval.TfIdfRetriever;
import com.example.springboot.faq.service.FaqService;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Offline evidence-candidate/gate measurement. Never calls a provider or changes production retrieval. */
class FaqCorpusBaselineTest {
    private final ObjectMapper json = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    private final Path root = Paths.get(System.getProperty("basedir", ".")).toAbsolutePath().getParent();
    private final Path corpusPath = root.resolve("database/faq_ccnu_corpus_20261111.json");
    private final Path evalPath = root.resolve("docs/faq-evaluation");
    private final TfIdfRetriever retriever = new TfIdfRetriever();

    @Test void corpusAndSplitsAreSourceCheckedDisjointAndWithinExistingBudgets() throws Exception {
        JsonNode corpus = read(corpusPath);
        List<FaqEntry> entries = entries(corpus);
        assertEquals(40, entries.size());
        Set<String> keys = new HashSet<>(), natural = new HashSet<>(), questions = new HashSet<>();
        int ordinal = 0;
        for (JsonNode row : corpus.path("entries")) {
            assertEquals(String.format(Locale.ROOT, "CCNU-FAQ-%03d", ++ordinal), row.path("key").asText());
            assertTrue(keys.add(row.path("key").asText()));
            JsonNode source = corpus.path("sources").path(row.path("sourceKey").asText());
            assertEquals("2026-11-11", source.path("checkedAt").asText());
            assertFalse(source.path("locator").asText().isEmpty());
            String host = URI.create(source.path("url").asText()).getHost();
            assertTrue("ccnu.edu.cn".equals(host) || host.endsWith(".ccnu.edu.cn"));
            assertTrue(natural.add(row.path("question").asText() + "\n" + source.path("url").asText()));
            assertTrue(questions.add(row.path("question").asText().toLowerCase(Locale.ROOT)));
        }
        for (FaqEntry row : entries) {
            assertTrue(row.getQuestion().length() + row.getAnswer().length() <= 2000);
            assertTrue(row.getEnabled());
        }
        Set<String> evaluationQuestions = new HashSet<>(), ids = new HashSet<>();
        for (String split : new String[]{"calibration", "holdout"}) {
            JsonNode cases = read(evalPath.resolve(split + ".json")).path("cases");
            assertTrue(cases.size() >= ("holdout".equals(split) ? 30 : 10));
            for (JsonNode item : cases) {
                assertTrue(ids.add(item.path("id").asText()));
                String question = item.path("question").asText();
                assertFalse(questions.contains(question.toLowerCase(Locale.ROOT)), "No copied stored questions: " + question);
                assertTrue(evaluationQuestions.add(question.toLowerCase(Locale.ROOT)), "Splits overlap");
                assertTrue(item.path("expectedKeys").isArray());
                for (JsonNode key : item.path("expectedKeys")) assertTrue(keys.contains(key.asText()));
                assertFalse(item.path("reason").asText().isEmpty());
            }
        }
    }

    @Test void museumGroupBoundaryIncludesExactlyTenVisitorsAsTheOfficialGuideStates() throws Exception {
        for (JsonNode row : read(corpusPath).path("entries")) {
            if ("CCNU-FAQ-038".equals(row.path("key").asText())) {
                assertTrue(row.path("answer").asText().contains("ten or more"), "Official 10人以上 includes exactly ten");
                return;
            }
        }
        fail("Museum reservation knowledge missing");
    }

    @Test void additiveSqlContainsExactlyTheReviewedNewQuestionsAndNoDestructiveStatements() throws Exception {
        String sql = new String(Files.readAllBytes(root.resolve("database/faq_ccnu_expansion_20261111.sql")), StandardCharsets.UTF_8);
        assertEquals(35, sql.split("INSERT INTO dormitory.faq_entry", -1).length - 1);
        assertFalse(java.util.regex.Pattern.compile("(?im)^\\s*(UPDATE|DELETE|DROP|ALTER|TRUNCATE|REPLACE)\\b").matcher(sql).find());
        assertTrue(sql.contains("START TRANSACTION;")); assertTrue(sql.trim().endsWith("COMMIT;"));
        int index = 0;
        for (JsonNode row : read(corpusPath).path("entries")) {
            if (index++ < 5) continue;
            assertTrue(sql.contains("-- " + row.path("key").asText()));
            List<String> fields = new ArrayList<>();
            for (String name : Arrays.asList("question", "answer", "category", "keywords", "sourceTitle")) {
                fields.add(quote(row.path(name).asText()));
            }
            String url = read(corpusPath).path("sources").path(row.path("sourceKey").asText()).path("url").asText();
            fields.add(quote(url));
            assertTrue(sql.contains("SELECT " + String.join(", ", fields) + ", 1"), "SQL values differ from corpus");
            assertTrue(sql.contains("WHERE question = " + quote(row.path("question").asText()) + " AND source_url = " + quote(url)));
        }
    }

    @Test void reportMeasuresFrozenDatasetsWithOriginalThresholdAndSeparatesUnmeasuredModelQuality() throws Exception {
        JsonNode corpus = read(corpusPath);
        List<FaqEntry> expanded = entries(corpus);
        ObjectNode report = json.createObjectNode().put("version", "2026-11-11")
                .put("threshold", 0.35).put("topK", 3).put("providerCalls", 0)
                .put("scope", "Offline retrieval candidates and C5 gate only; no live rewrite or generation quality measured.")
                .put("corpusSha256", hash(corpusPath));
        ObjectNode snapshots = report.putObject("snapshots");
        for (String split : new String[]{"calibration", "holdout"}) {
            Path path = evalPath.resolve(split + ".json");
            JsonNode cases = read(path).path("cases");
            report.put(split + "Sha256", hash(path));
            ObjectNode result = snapshots.putObject(split);
            result.set("initial5", measure(expanded.subList(0, 5), cases));
            result.set("expanded40", measure(expanded, cases));
        }
        JsonNode dialogue = read(evalPath.resolve("dialogue.json")).path("cases");
        report.put("dialogueSha256", hash(evalPath.resolve("dialogue.json")));
        ArrayNode gates = report.putArray("dialogueRouting");
        int correct = 0;
        for (JsonNode item : dialogue) {
            boolean actual = LlmClient.needsContext(item.path("question").asText());
            boolean expected = !"direct".equals(item.path("expectedRoute").asText());
            if (actual == expected) correct++;
            gates.addObject().put("id", item.path("id").asText())
                    .put("expectedRoute", item.path("expectedRoute").asText()).put("actualNeedsContext", actual)
                    .put("gateCorrect", actual == expected)
                    .put("semanticResolution", "not_measured_no_provider_calls");
        }
        report.put("dialogueGateCorrect", correct).put("dialogueCases", dialogue.size());
        Path output = Paths.get(System.getProperty("basedir", ".")).resolve("target/faq-corpus-baseline");
        Files.createDirectories(output);
        json.writerWithDefaultPrettyPrinter().writeValue(output.resolve("baseline.json").toFile(), report);
        assertEquals(40, report.path("snapshots").path("holdout").path("expanded40").path("caseCount").asInt());
        System.out.println("FAQ_BASELINE " + report.path("snapshots").path("holdout").path("expanded40").path("metrics"));
    }

    @Test void metricCountingDoesNotTurnUnsupportedHighScoreMatchesIntoSuccessfulAnswers() throws Exception {
        JsonNode corpus = read(corpusPath);
        ArrayNode cases = json.createArrayNode();
        cases.addObject().put("id", "known").put("question", corpus.path("entries").get(0).path("question").asText())
                .putArray("expectedKeys").add("CCNU-FAQ-001");
        cases.addObject().put("id", "unsupported").put("question", corpus.path("entries").get(0).path("question").asText())
                .putArray("expectedKeys");
        JsonNode report = measure(entries(corpus), cases).path("metrics");
        assertEquals(1, report.path("answerable").asInt());
        assertEquals(1, report.path("correctTop1").asInt());
        assertEquals(1, report.path("unanswerable").asInt());
        assertEquals(1, report.path("unsupportedCandidateMatches").asInt());
    }

    private ObjectNode measure(List<FaqEntry> entries, JsonNode cases) {
        Map<Long, String> keyById = entries.stream().collect(Collectors.toMap(FaqEntry::getId,
                row -> String.format(Locale.ROOT, "CCNU-FAQ-%03d", row.getId())));
        FaqMapper mapper = mock(FaqMapper.class);
        when(mapper.findEnabled()).thenReturn(entries);
        LlmClient disabled = new LlmClient(new FaqLlmProperties(false, "", "", "", "max_tokens"), json, new RestTemplateBuilder());
        FaqService service = new FaqService(mapper, retriever, 0.35, disabled);
        ObjectNode result = json.createObjectNode().put("caseCount", cases.size()).put("corpusSize", entries.size());
        ArrayNode rows = result.putArray("cases");
        int positive = 0, covered = 0, top1 = 0, top3 = 0, rawTop3 = 0, negative = 0, unsupported = 0;
        for (JsonNode item : cases) {
            Set<String> expected = new HashSet<>(); item.path("expectedKeys").forEach(key -> expected.add(key.asText()));
            String question = item.path("question").asText();
            List<TfIdfRetriever.Match> raw = retriever.findTopMatches(question, entries, 3);
            List<TfIdfRetriever.Match> eligible = service.retrieveContexts(question);
            FaqAnswerResponse answer = service.ask(question);
            List<String> accepted = eligible.stream().map(match -> keyById.get(match.getEntry().getId())).collect(Collectors.toList());
            boolean hit = accepted.stream().anyMatch(expected::contains);
            boolean first = !accepted.isEmpty() && expected.contains(accepted.get(0));
            if (expected.isEmpty()) { negative++; if (!accepted.isEmpty()) unsupported++; }
            else {
                positive++;
                if (expected.stream().anyMatch(keyById::containsValue)) covered++;
                if (first) top1++;
                if (hit) top3++;
                if (raw.stream().anyMatch(match -> expected.contains(keyById.get(match.getEntry().getId())))) rawTop3++;
            }
            ObjectNode row = rows.addObject().put("id", item.path("id").asText())
                    .put("actualModeWithoutModel", answer.getMode()).put("top1Correct", first)
                    .put("recallAt3", hit).put("unsupportedCandidateMatch", expected.isEmpty() && !accepted.isEmpty());
            row.set("expectedKeys", item.path("expectedKeys"));
            ArrayNode candidates = row.putArray("candidates");
            for (TfIdfRetriever.Match match : raw) candidates.addObject()
                    .put("key", keyById.get(match.getEntry().getId())).put("score", match.getScore())
                    .put("aboveThreshold", match.getScore() >= 0.35);
        }
        result.putObject("metrics").put("answerable", positive).put("coveredAnswerable", covered)
                .put("correctTop1", top1).put("correctTop3", top3).put("rawTop3", rawTop3)
                .put("unanswerable", negative).put("unsupportedCandidateMatches", unsupported)
                .put("top1Rate", positive == 0 ? 0 : (double) top1 / positive)
                .put("recallAt3", positive == 0 ? 0 : (double) top3 / positive)
                .put("noCandidateRate", negative == 0 ? 0 : (double) (negative - unsupported) / negative);
        return result;
    }

    private List<FaqEntry> entries(JsonNode corpus) {
        List<FaqEntry> rows = new ArrayList<>();
        for (JsonNode item : corpus.path("entries")) {
            JsonNode source = corpus.path("sources").path(item.path("sourceKey").asText());
            FaqEntry row = new FaqEntry(); row.setId((long) rows.size() + 1); row.setEnabled(item.path("enabled").asBoolean());
            row.setQuestion(item.path("question").asText()); row.setAnswer(item.path("answer").asText());
            row.setCategory(item.path("category").asText()); row.setKeywords(item.path("keywords").asText());
            row.setSourceTitle(item.path("sourceTitle").asText()); row.setSourceUrl(source.path("url").asText()); rows.add(row);
        }
        return rows;
    }
    private JsonNode read(Path path) throws Exception { return json.readTree(Files.readAllBytes(path)); }
    private String quote(String value) { return "'" + value.replace("'", "''") + "'"; }
    private String hash(Path path) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path));
        StringBuilder value = new StringBuilder(); for (byte item : digest) value.append(String.format("%02x", item & 255));
        return value.toString();
    }
}
