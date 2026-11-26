package com.example.springboot.faq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Offline reports distinguish routing/source checks from human factual review. */
class FaqStep6AcceptanceReportTest {
    static final ObjectMapper JSON = new ObjectMapper();
    static final String HOLDOUT_SHA = "c9578fde00c928885aa56171aaec89d94b5d3e0025575e0a6217c780d16e0b77";
    static final String CORPUS_SHA = "55808b345d4c61fe2aedd4ba5ae89454818f3ebe5498912bbc8e0244c7f1cc8a";

    @Test void expectedSourceAndValidCitationDoNotBecomeFactualAccuracy() throws Exception {
        ObjectNode raw = fixture("generated", true, true, true);
        ObjectNode report = summarize(raw);
        assertNotNull(report, "Acceptance aggregation is not implemented yet");
        assertEquals(1, report.path("metrics").path("positiveExpectedSourceReturned").asInt());
        assertEquals(1, report.path("metrics").path("negativeAnswered").asInt());
        assertFalse(report.path("automaticQualityGatePassed").asBoolean());
        assertEquals("pending_manual_review", report.path("factualSupportStatus").asText());
        assertFalse(report.has("accuracy"));
        assertTrue(report.path("cases").get(0).path("requiresFactualReview").asBoolean());
    }

    @Test void outageNoMatchIsNotCountedAsEvidenceBasedRefusal() throws Exception {
        ObjectNode report = summarize(fixture("no_match", false, false, true));
        assertNotNull(report, "Acceptance aggregation is not implemented yet");
        assertEquals(0, report.path("metrics").path("negativeCorrectRefusal").asInt());
        assertEquals(1, report.path("metrics").path("unavailableCases").asInt());
        assertFalse(report.path("measurementComplete").asBoolean());
    }

    @Test @EnabledIfEnvironmentVariable(named = "FAQ_STEP6_FINAL_REPLAY_VERIFY", matches = "true")
    void replaySavedResponsesWithoutDatabaseOrNetwork() throws Exception {
        Path root = root();
        JsonNode raw = JSON.readTree(root.resolve("springboot/target/faq-step6-5-8/responses.json").toFile());
        assertEquals(HOLDOUT_SHA, sha(root.resolve("docs/faq-evaluation/holdout.json")));
        assertEquals(CORPUS_SHA, sha(root.resolve("database/faq_ccnu_corpus_20261111.json")));
        ObjectNode report = summarize(raw);
        JsonNode saved = JSON.readTree(root.resolve("docs/faq-evaluation/step6-final-20261118.json").toFile());
        // Compare persisted JSON semantics: Jackson reloads small LongNode values as IntNode.
        assertEquals(saved, JSON.readTree(JSON.writeValueAsBytes(report)),
                "Replay must reproduce the saved report exactly, without provider calls");
    }

    private static ObjectNode fixture(String negativeMode, boolean available, boolean negativeCallsOk, boolean positiveCallsOk) {
        ObjectNode raw = JSON.createObjectNode();
        raw.put("completed", true).put("databaseUnchanged", true).put("expectedCaseCount", 2);
        ArrayNode rows = raw.putArray("cases");
        rows.addObject().put("id", "positive").put("positive", true).put("mode", "generated")
                .put("expectedSourceReturned", true).put("citationMetadataValid", true)
                .put("retrievalAvailable", true).put("providerFailed", !positiveCallsOk).put("answer", "Claim [FAQ:1]");
        rows.addObject().put("id", "negative").put("positive", false).put("mode", negativeMode)
                .put("expectedSourceReturned", false).put("citationMetadataValid", true)
                .put("retrievalAvailable", available).put("providerFailed", !negativeCallsOk);
        return raw;
    }

    static ObjectNode summarize(JsonNode raw) {
        ObjectNode report = JSON.createObjectNode();
        for (String field : Arrays.asList("version", "startedAt", "finishedAt", "holdoutSha256", "corpusSha256",
                "policy", "generationModel", "generationEndpoint", "embeddingModel", "embeddingEndpoint",
                "deepSeekAttempts", "embeddingAttempts", "databaseUnchanged")) {
            if (raw.has(field)) report.set(field, raw.get(field).deepCopy());
        }
        report.put("factualSupportStatus", "pending_manual_review").put("readyForProduction", false)
                .put("citationValidityIsFactualSupport", false).put("fixedSetIsBlindThirdPartyTest", false)
                .put("metricScope", "Observed routing and returned-source metadata on this fixed public set; not answer accuracy");
        ArrayNode rows = report.putArray("cases");
        int positive = 0, negative = 0, expectedSources = 0, wrongSources = 0, positiveRefused = 0;
        int negativeRefused = 0, negativeAnswered = 0, unavailable = 0, invalidCitations = 0;
        int generated = 0, extractive = 0, top1 = 0, top3 = 0;
        List<Long> latency = new ArrayList<>();
        for (JsonNode original : raw.path("cases")) {
            ObjectNode row = original.deepCopy();
            rows.add(row);
            String mode = row.path("mode").asText();
            boolean isPositive = row.path("positive").asBoolean();
            boolean answered = "generated".equals(mode) || "extractive".equals(mode);
            boolean healthy = row.path("retrievalAvailable").asBoolean() && !row.path("providerFailed").asBoolean()
                    && !"unavailable".equals(mode);
            row.put("requiresFactualReview", answered).put("factualSupportVerdict", answered ? "pending" : "not_applicable");
            if (isPositive) {
                positive++;
                if (answered && row.path("expectedSourceReturned").asBoolean()) expectedSources++;
                if (answered && !row.path("expectedSourceReturned").asBoolean()) wrongSources++;
                if (healthy && "no_match".equals(mode)) positiveRefused++;
                JsonNode retrieved = row.path("retrievedKeys");
                for (JsonNode expected : row.path("expectedKeys")) {
                    if (retrieved.size() > 0 && expected.equals(retrieved.get(0))) { top1++; break; }
                }
                boolean retrievedExpected = false;
                for (JsonNode expected : row.path("expectedKeys")) for (JsonNode key : retrieved) retrievedExpected |= expected.equals(key);
                if (retrievedExpected) top3++;
            } else {
                negative++;
                if (answered) negativeAnswered++;
                if (healthy && "no_match".equals(mode)) negativeRefused++;
            }
            if (!healthy) unavailable++;
            if (!row.path("citationMetadataValid").asBoolean()) invalidCitations++;
            if ("generated".equals(mode)) generated++;
            if ("extractive".equals(mode)) extractive++;
            if (row.has("elapsedMillis")) latency.add(row.path("elapsedMillis").asLong());
        }
        boolean complete = raw.path("completed").asBoolean() && raw.path("databaseUnchanged").asBoolean()
                && rows.size() == raw.path("expectedCaseCount").asInt() && unavailable == 0;
        report.put("measurementComplete", complete);
        ObjectNode metrics = report.putObject("metrics");
        metrics.put("positiveCases", positive).put("negativeCases", negative)
                .put("positiveExpectedSourceReturned", expectedSources).put("positiveWrongSourceReturned", wrongSources)
                .put("positiveHealthyRefusal", positiveRefused).put("negativeCorrectRefusal", negativeRefused)
                .put("negativeAnswered", negativeAnswered).put("unavailableCases", unavailable)
                .put("citationMetadataInvalidCases", invalidCitations).put("generatedCases", generated)
                .put("extractiveCases", extractive).put("positiveRetrievalTop1", top1).put("positiveRetrievalTop3", top3);
        Collections.sort(latency);
        if (!latency.isEmpty()) metrics.put("p50Millis", latency.get((latency.size() - 1) / 2))
                .put("p95Millis", latency.get((int) Math.ceil(latency.size() * 0.95) - 1)).put("maxMillis", latency.get(latency.size() - 1));
        report.put("automaticQualityGatePassed", complete && negativeAnswered == 0 && wrongSources == 0
                && invalidCitations == 0 && expectedSources >= raw.path("policy").path("minimumPositiveExpectedSourceReturned").asInt(24));
        return report;
    }

    static Path root() { return Paths.get(System.getProperty("basedir", ".")).toAbsolutePath().getParent(); }

    static String sha(Path file) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file));
        StringBuilder value = new StringBuilder();
        for (byte item : digest) value.append(String.format("%02x", item & 255));
        return value.toString();
    }
}
