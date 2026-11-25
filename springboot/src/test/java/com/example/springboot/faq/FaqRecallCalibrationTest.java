package com.example.springboot.faq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Offline data/replay checks; this never runs a provider or fabricates a model quality score. */
class FaqRecallCalibrationTest {
    private final Path root = Paths.get(System.getProperty("basedir", ".")).toAbsolutePath().getParent();
    private final ObjectMapper json = new ObjectMapper();

    @Test void newPairedQuestionsHaveSupportedLabelsAndNoExactPriorQuestionReuse() throws Exception {
        JsonNode manifest = read("docs/faq-evaluation/calibration-recall-20261120.json");
        JsonNode corpus = read("database/faq_ccnu_corpus_20261111.json");
        assertEquals(manifest.path("corpusSha256").asText(), sha("database/faq_ccnu_corpus_20261111.json"));
        Map<String, JsonNode> entries = new HashMap<>();
        for (JsonNode entry : corpus.path("entries")) entries.put(entry.path("key").asText(), entry);
        Set<String> prior = new HashSet<>();
        // Collision check only: prior holdout labels/scores never select a parameter here.
        for (String file : Arrays.asList("calibration.json", "calibration-step6-20261117.json", "holdout.json", "dialogue.json")) {
            for (JsonNode row : read("docs/faq-evaluation/" + file).path("cases")) prior.add(normalize(row.path("question").asText()));
        }
        Set<String> ids = new HashSet<>(), questions = new HashSet<>();
        Map<String, Set<String>> pairs = new HashMap<>();
        int positives = 0, negatives = 0;
        for (JsonNode row : manifest.path("cases")) {
            String question = normalize(row.path("question").asText());
            assertFalse(question.isEmpty()); assertTrue(question.length() <= 1000);
            assertTrue(ids.add(row.path("id").asText())); assertTrue(questions.add(question));
            assertFalse(prior.contains(question), "Use new wording for tuning, never recycle fixed evaluation questions");
            String outcome = row.path("outcome").asText();
            assertTrue(Arrays.asList("answerable", "no_match").contains(outcome));
            assertTrue(pairs.computeIfAbsent(row.path("pair").asText(), key -> new HashSet<>()).add(outcome));
            assertFalse(row.path("reason").asText().trim().isEmpty());
            assertTrue(row.path("mustPreserveFacts").isArray());
            if ("answerable".equals(outcome)) {
                positives++; assertEquals(1, row.path("expectedKeys").size());
                JsonNode evidence = entries.get(row.path("expectedKeys").get(0).asText()); assertNotNull(evidence);
                for (JsonNode fact : row.path("mustPreserveFacts"))
                    assertTrue(evidence.path("answer").asText().toLowerCase(Locale.ROOT).contains(fact.asText().toLowerCase(Locale.ROOT)),
                            "Required literal facts must actually occur in the frozen source answer");
            } else {
                negatives++; assertEquals(0, row.path("expectedKeys").size());
                assertTrue(row.path("relatedKeys").size() > 0);
                for (JsonNode key : row.path("relatedKeys")) assertTrue(entries.containsKey(key.asText()));
            }
        }
        assertEquals(16, positives); assertEquals(16, negatives); assertEquals(16, pairs.size());
        for (Set<String> outcomes : pairs.values()) assertEquals(new HashSet<>(Arrays.asList("answerable", "no_match")), outcomes);
        assertFalse(manifest.path("realEmbeddingMeasured").asBoolean(true));
        assertFalse(manifest.path("realGenerationMeasured").asBoolean(true));
    }

    @Test void versionedGuidanceLabelsRequireTheirSourceQualifications() throws Exception {
        JsonNode manifest = read("docs/faq-evaluation/calibration-recall-20261120.json");
        Map<String, Set<String>> facts = new HashMap<>();
        for (JsonNode row : manifest.path("cases")) {
            Set<String> required = new HashSet<>();
            for (JsonNode fact : row.path("mustPreserveFacts")) required.add(fact.asText());
            facts.put(row.path("id").asText(), required);
        }
        assertTrue(facts.get("R12P").contains("special notices"));
        assertTrue(facts.get("R15P").contains("confirm current requirements"));
    }

    @Test void sensitivityReportReplaysSavedCalibrationScoresAndDoesNotClaimAnswerAccuracy() throws Exception {
        JsonNode report = read("docs/faq-evaluation/recall-sensitivity-20261120.json");
        JsonNode calibration = read("docs/faq-evaluation/calibration-step6-20261117.json");
        JsonNode scores = read("docs/faq-evaluation/step6-scores-20261117.json");
        assertEquals(sha("docs/faq-evaluation/calibration-step6-20261117.json"), report.path("calibrationSha256").asText());
        assertEquals(sha("docs/faq-evaluation/step6-scores-20261117.json"), report.path("scoresSha256").asText());
        assertEquals(sha("docs/faq-evaluation/calibration-recall-20261120.json"), report.path("newSamplesSha256").asText());
        Map<String, JsonNode> labels = new HashMap<>();
        for (JsonNode row : calibration.path("cases")) labels.put(row.path("id").asText(), row);
        List<ThresholdCalibration.ScoredCase> measured = new ArrayList<>();
        int clarifications = 0;
        for (JsonNode row : scores.path("cases")) {
            JsonNode label = labels.get(row.path("id").asText()); assertNotNull(label);
            if ("clarify".equals(label.path("outcome").asText())) {
                assertEquals(0, row.path("ranked").size()); clarifications++; continue;
            }
            assertEquals(40, row.path("ranked").size());
            Set<String> expected = new HashSet<>();
            for (JsonNode key : label.path("expectedKeys")) expected.add(key.asText());
            List<ThresholdCalibration.Hit> ranking = new ArrayList<>();
            for (JsonNode hit : row.path("ranked")) ranking.add(new ThresholdCalibration.Hit(hit.path("key").asText(), hit.path("score").asDouble()));
            measured.add(new ThresholdCalibration.ScoredCase(row.path("id").asText(), label.path("outcome").asText(), expected, ranking));
        }
        assertEquals(52, measured.size()); assertEquals(8, clarifications); assertEquals(6, report.path("rows").size());
        for (JsonNode row : report.path("rows")) {
            ThresholdCalibration.Metrics actual = ThresholdCalibration.measure(measured, row.path("candidateThreshold").asDouble());
            assertEquals(28, actual.positives); assertEquals(24, actual.negatives);
            assertEquals(actual.top3Supported, row.path("positiveExpectedEvidenceTop3").asInt());
            assertEquals(actual.wrongTop1Accepted, row.path("positiveWrongTop1").asInt());
            assertEquals(actual.negativeAccepted, row.path("negativeWithCandidates").asInt());
        }
        assertEquals(0, report.path("providerCalls").asInt(-1));
        assertFalse(report.path("holdoutRescored").asBoolean(true));
        assertFalse(report.path("readyForProduction").asBoolean(true));
        assertTrue(report.path("finalAnswerAccuracy").isNull());
        assertFalse(report.path("newSamplesMeasured").asBoolean(true));
    }

    private String normalize(String value) { return value.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " "); }
    private JsonNode read(String path) throws Exception { return json.readTree(root.resolve(path).toFile()); }
    private String sha(String path) throws Exception { return ThresholdCalibration.sha256(Files.readAllBytes(root.resolve(path))); }
}
