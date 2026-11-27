package com.example.springboot.faq;

import com.example.springboot.faq.entity.FaqEntry;
import com.example.springboot.faq.retrieval.TfIdfRetriever;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit Step4 free-model experiment; no Spring context/database/chat or production retrieval switch. */
@EnabledIfEnvironmentVariable(named="FAQ_EMBEDDING_LIVE_VERIFY",matches="true")
class FaqEmbeddingSelectionIT {
    @Test void compareEnglishCalibrationRankingWithoutUsingTheHoldout() throws Exception {
        ObjectMapper json=new ObjectMapper();
        Path module=Paths.get(System.getProperty("basedir",".")).toAbsolutePath(),root=module.getParent();
        Path corpusPath=root.resolve("database/faq_ccnu_corpus_20261111.json");
        Path calibrationPath=root.resolve("docs/faq-evaluation/calibration.json");
        JsonNode corpus=json.readTree(corpusPath.toFile()),cases=json.readTree(calibrationPath.toFile()).path("cases");
        assertEquals(40,corpus.path("entries").size()); assertEquals(12,cases.size());
        List<String> texts=new ArrayList<>(),keys=new ArrayList<>();
        List<FaqEntry> entries=new ArrayList<>();
        for(JsonNode row:corpus.path("entries")) {
            texts.add(row.path("question").asText()+" "+row.path("keywords").asText());keys.add(row.path("key").asText());
            FaqEntry entry=new FaqEntry();entry.setId((long)entries.size()+1);
            entry.setQuestion(row.path("question").asText());entry.setKeywords(row.path("keywords").asText());entries.add(entry);
        }
        for(JsonNode item:cases)texts.add(item.path("question").asText());
        int inputBytes=texts.stream().mapToInt(text->text.getBytes(StandardCharsets.UTF_8).length).sum();
        List<List<String>> batches=EmbeddingSelectionProbe.batches(texts);
        assertTrue(inputBytes<=20000 && batches.size()<=8,"Pilot exceeds approved byte/request bounds");
        String key=System.getenv("SILICONFLOW_API_KEY");
        assertTrue(key!=null && !key.trim().isEmpty(),"Set the dedicated pilot credential privately");
        Path output=module.resolve("target/faq-embedding-selection");Files.createDirectories(output);
        ObjectNode report=json.createObjectNode().put("version","2026-11-11")
                .put("startedAt",java.time.Instant.now().toString()).put("provider","SiliconFlow China")
                .put("endpoint",EmbeddingSelectionProbe.ENDPOINT).put("model",EmbeddingSelectionProbe.MODEL)
                .put("dimensions",EmbeddingSelectionProbe.DIMENSIONS).put("inputRepresentation","question + space + keywords")
                .put("corpusSha256",hash(Files.readAllBytes(corpusPath))).put("calibrationSha256",hash(Files.readAllBytes(calibrationPath)))
                .put("inputSha256",hash(json.writeValueAsBytes(texts))).put("holdoutUsed",false)
                .put("productionRetrievalChanged",false).put("inputUtf8Bytes",inputBytes);
        List<double[]> vectors=new ArrayList<>();int attempts=0;long tokens=0;boolean completed=false;
        ArrayNode batchTimes=report.putArray("batchMillis");
        try {
            for(List<String> batch:batches) {
                if(attempts>=8 || tokens+batch.stream().mapToInt(s->s.getBytes(StandardCharsets.UTF_8).length).sum()>40000)
                    throw new IllegalStateException("Embedding pilot request/token limit exhausted");
                attempts++; long started=System.nanoTime();
                EmbeddingSelectionProbe.Batch result=EmbeddingSelectionProbe.fetch(batch,key);
                batchTimes.add((System.nanoTime()-started)/1000000);tokens+=result.tokens;
                assertTrue(tokens<=40000,"Reported token usage exceeds the pilot budget");
                vectors.addAll(Arrays.asList(result.vectors));
            }
            assertEquals(texts.size(),vectors.size());
            json.writeValue(output.resolve("vectors.json").toFile(),vectors);
            report.put("vectorsSha256",hash(Files.readAllBytes(output.resolve("vectors.json"))));
            ArrayNode results=report.putArray("calibrationCases");
            int positives=0,negatives=0,embeddingTop1=0,embeddingTop3=0,lexicalTop1=0,lexicalTop3=0,lexicalAcceptedTop1=0;
            TfIdfRetriever retriever=new TfIdfRetriever();
            for(int c=0;c<cases.size();c++) {
                JsonNode item=cases.get(c); Set<String> expected=new HashSet<>();
                item.path("expectedKeys").forEach(value->expected.add(value.asText()));
                List<Integer> order=new ArrayList<>();for(int d=0;d<keys.size();d++)order.add(d);
                double[] query=vectors.get(keys.size()+c);
                order.sort(Comparator.<Integer>comparingDouble(index->EmbeddingSelectionProbe.cosine(query,vectors.get(index)))
                        .reversed().thenComparingInt(index->index));
                List<TfIdfRetriever.Match> lexical=retriever.findTopMatches(item.path("question").asText(),entries,3);
                ObjectNode row=results.addObject().put("id",item.path("id").asText());row.set("expectedKeys",item.path("expectedKeys"));
                ArrayNode semantic=row.putArray("embeddingRawTop3");
                for(int d:order.subList(0,3))semantic.addObject().put("key",keys.get(d))
                        .put("score",EmbeddingSelectionProbe.cosine(query,vectors.get(d)));
                ArrayNode baseline=row.putArray("tfidfRawTop3");
                for(TfIdfRetriever.Match match:lexical)baseline.addObject().put("key",keys.get(match.getEntry().getId().intValue()-1)).put("score",match.getScore());
                if(expected.isEmpty()){negatives++;row.put("negativeMaxScore",EmbeddingSelectionProbe.cosine(query,vectors.get(order.get(0))));}
                else {
                    positives++;
                    if(expected.contains(keys.get(order.get(0))))embeddingTop1++;
                    if(order.subList(0,3).stream().anyMatch(i->expected.contains(keys.get(i))))embeddingTop3++;
                    if(!lexical.isEmpty() && expected.contains(keys.get(lexical.get(0).getEntry().getId().intValue()-1))) {
                        lexicalTop1++;if(lexical.get(0).getScore()>=0.35)lexicalAcceptedTop1++;
                    }
                    if(lexical.stream().anyMatch(m->expected.contains(keys.get(m.getEntry().getId().intValue()-1))))lexicalTop3++;
                }
            }
            report.putObject("rankingMetrics").put("positiveCases",positives).put("negativeCases",negatives)
                    .put("embeddingRawTop1",embeddingTop1).put("embeddingRawRecallAt3",embeddingTop3)
                    .put("tfidfRawTop1",lexicalTop1).put("tfidfRawRecallAt3",lexicalTop3)
                    .put("tfidfTop1At035",lexicalAcceptedTop1)
                    .put("note","No embedding acceptance threshold selected; negative scores are diagnostics, not rejection accuracy.");
            System.out.println("EMBEDDING_PILOT "+report.path("rankingMetrics"));
            completed=true;
        } finally {
            report.put("providerRequests",attempts).put("reportedInputTokens",tokens)
                    .put("estimatedChargeCnyAtListPrice",0).put("paidFallbackAllowed",false)
                    .put("completed",completed);
            json.writerWithDefaultPrettyPrinter().writeValue(output.resolve("pilot.json").toFile(),report);
            System.out.println("EMBEDDING_PILOT requests="+attempts+" inputTokens="+tokens+" completed="+completed);
        }
    }
    private static String hash(byte[] bytes) throws Exception {
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(bytes);StringBuilder value=new StringBuilder();
        for(byte item:digest)value.append(String.format("%02x",item&255));return value.toString();
    }
}
