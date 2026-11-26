package com.example.springboot.faq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.example.springboot.faq.FaqStep6AcceptanceReportTest.*;

/** Counts observed behavior only; factual review is a separate bound artifact. */
class FaqRecallLiveReportTest {
    @Test void candidatesDoNotBecomeAnswersAndProviderFailureIsNotHealthyRefusal() {
        ObjectNode raw = fixture();
        ObjectNode positive = row(raw, "P", true, false, "no_match", true, "insufficient_evidence");
        positive.putArray("expectedKeys").add("A"); positive.putArray("retrievedKeys").add("A");
        row(raw, "N", false, false, "no_match", true, "failed").put("providerFailed", true);
        ObjectNode report = summarizeRecall(raw);
        assertEquals(1, report.path("metrics").path("positiveCandidateTop3").asInt());
        assertEquals(0, report.path("metrics").path("positiveExpectedSourceAnswers").asInt());
        assertEquals(1, report.path("metrics").path("positiveModelAbstentions").asInt());
        assertEquals(0, report.path("metrics").path("negativeHealthyRefusals").asInt());
        assertEquals(1, report.path("metrics").path("unavailableCases").asInt());
        assertFalse(report.path("measurementComplete").asBoolean());
    }

    @Test void diagnosticCasesAreSeparateAndInvalidCitationsRemainVisible() {
        ObjectNode raw = fixture();
        row(raw, "P", true, false, "generated", true, "generated")
                .put("expectedSourceReturned", true).put("citationMetadataValid", false);
        row(raw, "N", false, false, "generated", true, "generated");
        row(raw, "H22", true, true, "generated", true, "generated").put("expectedSourceReturned", true);
        ObjectNode report = summarizeRecall(raw);
        assertEquals(1, report.path("metrics").path("positiveCases").asInt());
        assertEquals(1, report.path("metrics").path("negativeAnswered").asInt());
        assertEquals(1, report.path("metrics").path("invalidCitationCases").asInt());
        assertEquals(1, report.path("diagnostics").size());
        assertFalse(report.has("automaticQualityGatePassed"));
        assertFalse(report.path("readyForProduction").asBoolean());
        assertEquals("pending_manual_review", report.path("factualSupportStatus").asText());
    }

    @Test void missingCasesCannotProduceACompleteMeasurement() {
        ObjectNode raw = fixture(); raw.put("expectedCaseCount", 34);
        row(raw, "N", false, false, "no_match", true, "not_called");
        assertFalse(summarizeRecall(raw).path("measurementComplete").asBoolean());
    }

    @Test void discardedUnavailablePlaceholdersAreNotSemanticRecall() {
        ObjectNode raw=fixture();
        ObjectNode row=row(raw,"P",true,false,"no_match",false,"not_called");
        row.putArray("expectedKeys").add("A"); row.withArray("retrievedKeys").add("A");
        assertEquals(0,summarizeRecall(raw).path("metrics").path("positiveCandidateTop3").asInt());
    }

    @Test void generatedButNotReturnedAndSkippedGenerationAreNotHealthyRefusals() {
        ObjectNode raw=fixture();
        row(raw,"N1",false,false,"no_match",true,"generated");
        row(raw,"N2",false,false,"no_match",true,"not_called").withArray("retrievedKeys").add("A");
        ObjectNode report=summarizeRecall(raw);
        assertEquals(0,report.path("metrics").path("negativeHealthyRefusals").asInt());
        assertEquals(2,report.path("metrics").path("unavailableCases").asInt());
    }

    @Test @EnabledIfEnvironmentVariable(named="FAQ_RECALL_LIVE_REPLAY_VERIFY", matches="true")
    void replaySavedLiveResultsWithoutProviderOrDatabase() throws Exception {
        JsonNode raw = JSON.readTree(root().resolve("springboot/target/faq-recall-live-20261122/responses.json").toFile());
        assertEquals(raw.path("samplesSha256").asText(), sha(root().resolve("docs/faq-evaluation/calibration-recall-20261120.json")));
        JsonNode saved = JSON.readTree(root().resolve("docs/faq-evaluation/recall-live-20261122.json").toFile());
        assertEquals(saved, JSON.readTree(JSON.writeValueAsBytes(summarizeRecall(raw))));
    }

    @Test @EnabledIfEnvironmentVariable(named="FAQ_DELIVERY_REPLAY_VERIFY", matches="true")
    void replayDeliveryResultsWithoutProviderOrDatabase() throws Exception {
        JsonNode raw=JSON.readTree(root().resolve("springboot/target/faq-delivery-final-20261122/responses.json").toFile());
        assertEquals(HOLDOUT_SHA,raw.path("samplesSha256").asText());
        assertEquals(CORPUS_SHA,raw.path("corpusSha256").asText());
        JsonNode saved=JSON.readTree(root().resolve("docs/faq-evaluation/delivery-final-20261122.json").toFile());
        assertEquals(saved,JSON.readTree(JSON.writeValueAsBytes(summarizeRecall(raw))));
    }

    private static ObjectNode fixture() {
        ObjectNode raw = JSON.createObjectNode().put("completed", true).put("databaseUnchanged", true).put("expectedCaseCount", 2);
        raw.putArray("cases"); return raw;
    }

    private static ObjectNode row(ObjectNode raw, String id, boolean positive, boolean diagnostic,
                                   String mode, boolean available, String generation) {
        ObjectNode row = raw.withArray("cases").addObject().put("id", id).put("positive", positive)
                .put("diagnostic", diagnostic).put("mode", mode).put("retrievalAvailable", available)
                .put("generationOutcome", generation).put("providerFailed", false).put("citationMetadataValid", true);
        row.putArray("expectedKeys"); row.putArray("retrievedKeys"); return row;
    }

    static ObjectNode summarizeRecall(JsonNode raw) {
        ObjectNode report=JSON.createObjectNode();
        for (String field:Arrays.asList("version","startedAt","finishedAt","samplesSha256","corpusSha256","policy",
                "generationModel","generationEndpoint","embeddingModel","embeddingEndpoint","deepSeekAttempts",
                "embeddingAttempts","databaseUnchanged","databaseVerificationFailed"))
            if (raw.has(field)) report.set(field,raw.get(field).deepCopy());
        report.put("readyForProduction",false).put("isIndependentAcceptance",false)
                .put("factualSupportStatus","pending_manual_review").put("citationValidityIsFactualSupport",false);
        ArrayNode cases=report.putArray("cases"), diagnostics=report.putArray("diagnostics");
        int positives=0,negatives=0,top1=0,top3=0,positiveAnswers=0,wrongSources=0,modelAbstentions=0,belowFloor=0;
        int healthyNegativeRefusals=0,negativeAnswered=0,negativeCandidates=0,unavailable=0,invalidCitations=0,allUnavailable=0;
        List<Long> times=new ArrayList<>(); ObjectNode failures=report.putObject("failureTypes");
        for (JsonNode original:raw.path("cases")) {
            ObjectNode row=original.deepCopy();
            String mode=row.path("mode").asText(); boolean answered="generated".equals(mode)||"extractive".equals(mode);
            String generation=row.path("generationOutcome").asText();
            boolean healthyRefusal="no_match".equals(mode) && ("insufficient_evidence".equals(generation)
                    || ("not_called".equals(generation)&&row.path("retrievedKeys").size()==0));
            boolean healthy=row.path("retrievalAvailable").asBoolean()&&!row.path("providerFailed").asBoolean()
                    &&(("generated".equals(mode)&&"generated".equals(generation))||healthyRefusal);
            row.put("requiresFactualReview",answered).put("factualSupportVerdict",answered?"pending":"not_applicable");
            if (!healthy) allUnavailable++;
            for (String field:Arrays.asList("generationFailureType","embeddingFailureType","requestFailureType"))
                if (row.has(field)) { String type=row.path(field).asText(); failures.put(type,failures.path(type).asInt()+1); }
            if (row.path("diagnostic").asBoolean()) { diagnostics.add(row); continue; }
            cases.add(row);
            if (row.path("positive").asBoolean()) {
                positives++; boolean correctTop3=false;
                for (JsonNode key:row.path("retrievalAvailable").asBoolean()?row.path("expectedKeys"):JSON.createArrayNode()) {
                    if (row.path("retrievedKeys").size()>0 && key.equals(row.path("retrievedKeys").get(0))) top1++;
                    for (JsonNode hit:row.path("retrievedKeys")) correctTop3 |= key.equals(hit);
                }
                if (correctTop3) top3++;
                if (answered && row.path("expectedSourceReturned").asBoolean()) positiveAnswers++;
                if (answered && !row.path("expectedSourceReturned").asBoolean()) wrongSources++;
                if (healthy && "no_match".equals(mode) && "insufficient_evidence".equals(row.path("generationOutcome").asText())) modelAbstentions++;
                if (healthy && "no_match".equals(mode) && "not_called".equals(row.path("generationOutcome").asText())) belowFloor++;
            } else {
                negatives++;
                if (answered) negativeAnswered++;
                if (healthy && "no_match".equals(mode)) healthyNegativeRefusals++;
                if (row.path("retrievedKeys").size()>0) negativeCandidates++;
            }
            if (!healthy) unavailable++;
            if (!row.path("citationMetadataValid").asBoolean()) invalidCitations++;
            if (row.has("elapsedMillis")) times.add(row.path("elapsedMillis").asLong());
        }
        report.put("measurementComplete",raw.path("completed").asBoolean()&&raw.path("databaseUnchanged").asBoolean()
                &&raw.path("cases").size()==raw.path("expectedCaseCount").asInt()&&allUnavailable==0);
        report.putObject("metrics").put("positiveCases",positives).put("negativeCases",negatives)
                .put("positiveCandidateTop1",top1).put("positiveCandidateTop3",top3)
                .put("positiveExpectedSourceAnswers",positiveAnswers).put("positiveWrongSourceAnswers",wrongSources)
                .put("positiveModelAbstentions",modelAbstentions).put("positiveBelowCandidateFloor",belowFloor)
                .put("negativeHealthyRefusals",healthyNegativeRefusals).put("negativeAnswered",negativeAnswered)
                .put("negativeWithCandidates",negativeCandidates).put("unavailableCases",unavailable).put("invalidCitationCases",invalidCitations);
        Collections.sort(times);
        if (!times.isEmpty()) report.putObject("singleRunLatencyMillis").put("p50",times.get((times.size()-1)/2))
                .put("p95",times.get((int)Math.ceil(times.size()*.95)-1)).put("max",times.get(times.size()-1));
        return report;
    }
}
