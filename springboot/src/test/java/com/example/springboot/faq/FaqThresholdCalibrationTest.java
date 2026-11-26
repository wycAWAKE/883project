package com.example.springboot.faq;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FaqThresholdCalibrationTest {
    @Test void fullCorpusRankingIncludesCandidatesWithoutLexicalOverlapAndUsesStableTies() {
        List<ThresholdCalibration.Hit> ranked = ThresholdCalibration.rank(Arrays.asList("B", "A", "C"),
                Arrays.asList(new double[]{1, 0}, new double[]{1, 0}, new double[]{0, 1}), new double[]{1, 0});
        assertEquals(Arrays.asList("A", "B", "C"), Arrays.asList(ranked.get(0).key, ranked.get(1).key, ranked.get(2).key));
        assertEquals(1, ranked.get(0).score, 1e-12);
        assertEquals(0, ranked.get(2).score, 1e-12);
    }

    @Test void cosineHandlesExtremeFiniteMagnitudesAndRejectsInvalidVectors() {
        assertEquals(1, ThresholdCalibration.cosine(new double[]{1e308, 1e308}, new double[]{1e-300, 1e-300}), 1e-12);
        assertThrows(IllegalArgumentException.class, () -> ThresholdCalibration.cosine(new double[]{0}, new double[]{1}));
        assertThrows(IllegalArgumentException.class, () -> ThresholdCalibration.cosine(new double[]{Double.NaN}, new double[]{1}));
        assertThrows(IllegalArgumentException.class, () -> ThresholdCalibration.cosine(new double[]{1}, new double[]{1, 0}));
        assertThrows(IllegalArgumentException.class, () -> ThresholdCalibration.rank(Arrays.asList("A", "A"),
                Arrays.asList(new double[]{1}, new double[]{1}), new double[]{1}));
    }

    @Test void thresholdIsInclusiveAndAppliedToEachCandidateBeforeTopThree() {
        ThresholdCalibration.ScoredCase positive = sample("P", "answerable", "B", hit("A", .8), hit("B", .7), hit("C", .6));
        ThresholdCalibration.Metrics atBoundary = ThresholdCalibration.measure(Collections.singletonList(positive), .7);
        assertEquals(1, atBoundary.wrongTop1Accepted);
        assertEquals(1, atBoundary.top3Supported);
        assertEquals(0, atBoundary.positiveRejected);
        ThresholdCalibration.Metrics aboveBoundary = ThresholdCalibration.measure(Collections.singletonList(positive), Math.nextUp(.7));
        assertEquals(0, aboveBoundary.top3Supported);
        assertEquals(1, aboveBoundary.acceptedPositiveWithoutTop3Support);
    }

    @Test void wrongPositiveTopOneIsUnsafeEvenWhenCorrectEvidenceAppearsInTopThree() {
        List<ThresholdCalibration.ScoredCase> rows = Arrays.asList(
                sample("P1", "answerable", "B", hit("A", .9), hit("B", .8)),
                sample("P2", "answerable", "B", hit("B", .65), hit("A", .3)),
                sample("N", "no_match", null, hit("A", .85), hit("B", .5)),
                sample("A", "clarify", null));
        ThresholdCalibration.Metrics stats = ThresholdCalibration.measure(rows, .7);
        assertEquals(2, stats.positives); assertEquals(1, stats.negatives); assertEquals(1, stats.clarifications);
        assertEquals(0, stats.correctTop1Accepted); assertEquals(1, stats.wrongTop1Accepted);
        assertEquals(1, stats.negativeAccepted); assertEquals(1, stats.positiveRejected);
        assertEquals(1, stats.top3Supported);
    }

    @Test void candidateSelectionRequiresBothKindsOfUnsafeAcceptsToBeZeroAndReportsAllReject() {
        List<ThresholdCalibration.ScoredCase> rows = Arrays.asList(
                sample("P", "answerable", "A", hit("A", .8), hit("B", .2)),
                sample("N", "no_match", null, hit("A", .7), hit("B", .1)));
        List<ThresholdCalibration.Row> sweep = ThresholdCalibration.sweep(rows);
        assertEquals(100, sweep.size());
        ThresholdCalibration.Row selected = ThresholdCalibration.select(sweep);
        assertEquals(.71, selected.threshold, 1e-12);
        assertEquals(1, selected.metrics.correctTop1Accepted);
        List<ThresholdCalibration.ScoredCase> impossible = Arrays.asList(
                sample("P", "answerable", "A", hit("A", .7)), sample("N", "no_match", null, hit("A", .9)));
        ThresholdCalibration.Row rejectAll = ThresholdCalibration.select(ThresholdCalibration.sweep(impossible));
        assertEquals(0, rejectAll.metrics.correctTop1Accepted);
        assertEquals(1, rejectAll.metrics.positiveRejected);
        assertNull(ThresholdCalibration.select(ThresholdCalibration.sweep(Collections.singletonList(
                sample("N", "no_match", null, hit("A", 1))))));
    }

    @Test void malformedScoresAndLabelsCannotDisappearFromMetrics() {
        assertThrows(IllegalArgumentException.class, () -> ThresholdCalibration.measure(Collections.singletonList(
                sample("P", "answerable", null, hit("A", .8))), .5));
        assertThrows(IllegalArgumentException.class, () -> ThresholdCalibration.measure(Collections.singletonList(
                sample("N", "no_match", null, hit("A", Double.NaN))), .5));
        assertThrows(IllegalArgumentException.class, () -> ThresholdCalibration.measure(Collections.singletonList(
                sample("N", "unknown", null, hit("A", .8))), .5));
        assertThrows(IllegalArgumentException.class, () -> ThresholdCalibration.measure(Collections.singletonList(
                sample("N", "no_match", null)), .5));
    }

    @Test void stepFiveAllRejectedFallbackRestoresStableTieOrder() {
        Map<String, Double> lexical = new HashMap<>(); lexical.put("B", .3); lexical.put("A", .2); lexical.put("C", .1);
        List<ThresholdCalibration.Hit> ranked = ThresholdCalibration.step5Ranking(
                Arrays.asList(hit("B", .8), hit("A", .7), hit("C", .6)), lexical);
        assertEquals(Arrays.asList("A", "B", "C"), Arrays.asList(ranked.get(0).key, ranked.get(1).key, ranked.get(2).key));
        assertEquals(0, ThresholdCalibration.measure(Collections.singletonList(
                new ThresholdCalibration.ScoredCase("N", "no_match", Collections.emptySet(), ranked)), 0).negativeAccepted);
        lexical.put("B", .5);
        assertEquals("B", ThresholdCalibration.step5Ranking(Arrays.asList(hit("A", .8), hit("B", -.2), hit("C", -.3)), lexical).get(0).key);
    }

    @Test void versionedDatasetKeepsOriginalCasesAndUsesNoExactHoldoutQuestions() throws Exception {
        Path root = Paths.get(System.getProperty("basedir", ".")).toAbsolutePath().getParent();
        ObjectMapper json = new ObjectMapper();
        JsonNode expanded = json.readTree(root.resolve("docs/faq-evaluation/calibration-step6-20261117.json").toFile());
        JsonNode original = json.readTree(root.resolve("docs/faq-evaluation/calibration.json").toFile());
        JsonNode corpus = json.readTree(root.resolve("database/faq_ccnu_corpus_20261111.json").toFile());
        assertEquals(60, expanded.path("cases").size());
        Set<String> keys = new HashSet<>(); for (JsonNode row : corpus.path("entries")) keys.add(row.path("key").asText());
        Map<String, JsonNode> byId = new HashMap<>(); Set<String> questions = new HashSet<>();
        Map<String, Integer> counts = new HashMap<>();
        for (JsonNode row : expanded.path("cases")) {
            assertNull(byId.put(row.path("id").asText(), row));
            assertTrue(questions.add(normalize(row.path("question").asText())));
            String outcome = row.path("outcome").asText(); counts.merge(outcome, 1, Integer::sum);
            assertEquals("answerable".equals(outcome), row.path("expectedKeys").size() > 0);
            for (JsonNode key : row.path("expectedKeys")) assertTrue(keys.contains(key.asText()));
            for (JsonNode key : row.path("relatedKeys")) assertTrue(keys.contains(key.asText()));
            assertFalse(row.path("reason").asText().trim().isEmpty());
        }
        assertEquals(Integer.valueOf(28), counts.get("answerable")); assertEquals(Integer.valueOf(24), counts.get("no_match"));
        assertEquals(Integer.valueOf(8), counts.get("clarify"));
        for (JsonNode row : original.path("cases")) {
            for (Iterator<Map.Entry<String, JsonNode>> fields = row.fields(); fields.hasNext();) {
                Map.Entry<String, JsonNode> field = fields.next(); assertEquals(field.getValue(), byId.get(row.path("id").asText()).get(field.getKey()));
            }
        }
        // Exact text collision guard only; holdout labels/scores are not inspected or used to tune anything.
        for (JsonNode row : json.readTree(root.resolve("docs/faq-evaluation/holdout.json").toFile()).path("cases"))
            assertFalse(questions.contains(normalize(row.path("question").asText())), "Calibration/holdout exact collision");
        assertEquals("55808b345d4c61fe2aedd4ba5ae89454818f3ebe5498912bbc8e0244c7f1cc8a",
                ThresholdCalibration.sha256(Files.readAllBytes(root.resolve("database/faq_ccnu_corpus_20261111.json"))));
    }

    private String normalize(String text) { return text.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " "); }
    private ThresholdCalibration.Hit hit(String key, double score) { return new ThresholdCalibration.Hit(key, score); }
    private ThresholdCalibration.ScoredCase sample(String id, String outcome, String expected, ThresholdCalibration.Hit... hits) {
        return new ThresholdCalibration.ScoredCase(id, outcome, expected == null ? Collections.emptySet() : Collections.singleton(expected), Arrays.asList(hits));
    }
}
