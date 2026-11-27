package com.example.springboot.faq;

import com.example.springboot.faq.config.FaqLlmProperties;
import com.example.springboot.faq.dto.*;
import com.example.springboot.faq.entity.FaqEntry;
import com.example.springboot.faq.llm.LlmClient;
import com.example.springboot.faq.mapper.FaqMapper;
import com.example.springboot.faq.retrieval.*;
import com.example.springboot.faq.support.FaqRequestBudget;
import com.example.springboot.faq.service.FaqService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import javax.sql.DataSource;
import com.zaxxer.hikari.HikariDataSource;
import java.net.URI;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.regex.*;
import static com.example.springboot.faq.FaqStep6AcceptanceReportTest.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Explicit 32-question tuning pass + two historical diagnostics, never an ordinary test. */
@ActiveProfiles({"faq-deepseek", "faq-embedding"})
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE, properties={
        "spring.main.banner-mode=off", "logging.level.root=WARN", "faq.deepseek.enabled=true",
        "faq.deepseek.base-url=https://api.deepseek.com", "faq.deepseek.model=deepseek-flash",
        "spring.datasource.url=jdbc:mysql://localhost:3306/dormitory?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true",
        "spring.datasource.type=com.zaxxer.hikari.HikariDataSource", "spring.datasource.hikari.jdbc-url=${spring.datasource.url}",
        "spring.sql.init.mode=never", "spring.datasource.initialization-mode=never",
        "faq.embedding.enabled=true", "faq.embedding.semantic-acceptance-enabled=true",
        "faq.embedding.semantic-threshold=0.73", "faq.embedding.candidate-threshold=0.60"})
class FaqRecallLiveIT {
    static final String SAMPLES="docs/faq-evaluation/calibration-recall-20261120.json";
    static final String SAMPLES_SHA="94a294511555a97fd9681bc781beaeff7db6406c7519ab854d5af845a440ee3f";
    static final int MAX_GENERATIONS=34, MAX_EMBEDDINGS=37;
    static final String PRIOR_SHA="b13cd4f4ef5ee10151c66fab37394202afb4e9cc61194e47bd1b1ca92eeeeb1c";
    @Autowired FaqService service;
    @Autowired FaqMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired FaqLlmProperties model;
    @Autowired Environment environment;
    @SpyBean LlmClient llm;
    @SpyBean EmbeddingClient embedding;
    @SpyBean SemanticFaqRetriever semantic;
    private final ObjectNode raw=JSON.createObjectNode(), ledger=JSON.createObjectNode();
    private final Map<Long,String> keys=new HashMap<>();
    private final Map<Long,FaqEntry> entries=new HashMap<>();
    private final Set<String> documentTexts=new HashSet<>();
    private ObjectNode activeCase;
    private Path output;
    private int generations, embeddings;

    protected String sampleFile=SAMPLES, sampleSha=SAMPLES_SHA, runName="recall-live-20261122";
    protected String budgetVariable="FAQ_RECALL_LIVE_BUDGET";
    protected int mainCount=32, maxGenerations=MAX_GENERATIONS, maxEmbeddings=MAX_EMBEDDINGS;
    protected Set<String> diagnosticIdsToRun=new HashSet<>(Arrays.asList("H22","H26"));
    protected Map<String,String> expectedSources=new LinkedHashMap<>();

    @Test @EnabledIfEnvironmentVariable(named="FAQ_RECALL_LIVE_VERIFY", matches="true")
    void oneReadOnlyPassWithoutRetriesOrRetuning() throws Exception {
        assertEquals(maxGenerations+":"+maxEmbeddings, System.getenv(budgetVariable), "Fresh explicit call-budget authorization required");
        output=root().resolve("springboot/target/faq-"+runName);
        Files.createDirectories(output);
        Path claim=root().resolve("docs/faq-evaluation/"+runName+"-claim.json");
        assertFalse(Files.exists(claim),"Permanent claim exists; Maven clean does not authorize another run");
        assertFalse(Files.exists(root().resolve("docs/faq-evaluation/"+runName+".json")),"Prior permanent report exists");
        assertFalse(Files.exists(output.resolve("live-attempt.json")), "Attempt already exists; replay, never delete its ledger to retry");
        assertEquals(sampleSha, sha(root().resolve(sampleFile)));
        assertEquals(CORPUS_SHA, sha(root().resolve("database/faq_ccnu_corpus_20261111.json")));
        assertTrue(model.isEnabled()); assertTrue(embedding.isEnabled()); assertTrue(semantic.isSemanticAcceptanceEnabled());
        assertEquals("https://api.deepseek.com/chat/completions", model.getEndpoint().toString());
        assertEquals("deepseek-flash", model.getModel()); assertEquals("max_tokens", model.getOutputTokenParameter());
        assertEquals("https://api.siliconflow.cn/v1/embeddings", EmbeddingClient.ENDPOINT);
        assertEquals("BAAI/bge-m3", EmbeddingClient.MODEL); assertEquals(1024, EmbeddingClient.DIMENSIONS);
        assertEquals(.60, environment.getRequiredProperty("faq.embedding.candidate-threshold", Double.class));
        assertEquals(.73, environment.getRequiredProperty("faq.embedding.semantic-threshold", Double.class));
        validateLocalDataSource(dataSource);
        try (java.sql.Connection connection=dataSource.getConnection()) {
            URI uri=URI.create(connection.getMetaData().getURL().substring(5));
            assertTrue(Arrays.asList("localhost","127.0.0.1").contains(uri.getHost())); assertEquals(3306,uri.getPort());
        }
        assertEquals("dormitory", jdbc.queryForObject("SELECT DATABASE()",String.class));
        List<Map<String,Object>> before=jdbc.queryForList("SELECT * FROM faq_entry ORDER BY id");
        validateCorpus(before);
        ArrayNode cases=JSON.createArrayNode();
        for (JsonNode row:JSON.readTree(root().resolve(sampleFile).toFile()).path("cases")) cases.add(row.deepCopy());
        assertEquals(mainCount,cases.size());
        // Only these already-exposed failures are diagnostic; they never enter tuning metrics.
        assertEquals(PRIOR_SHA,sha(root().resolve("docs/faq-evaluation/step6-final-20261118.json")));
        JsonNode prior=JSON.readTree(root().resolve("docs/faq-evaluation/step6-final-20261118.json").toFile());
        Set<String> diagnosticIds=new HashSet<>();
        for (JsonNode row:prior.path("cases")) if (diagnosticIdsToRun.contains(row.path("id").asText())) {
            assertTrue(diagnosticIds.add(row.path("id").asText()));
            ObjectNode diagnostic=row.deepCopy(); diagnostic.put("diagnostic",true); cases.add(diagnostic);
        }
        assertEquals(diagnosticIdsToRun,diagnosticIds);
        assertEquals(mainCount+diagnosticIdsToRun.size(),cases.size());
        raw.put("version",runName).put("startedAt",Instant.now().toString())
                .put("expectedCaseCount",mainCount+diagnosticIdsToRun.size()).put("samplesSha256",sampleSha).put("corpusSha256",CORPUS_SHA)
                .put("completed",false).put("databaseUnchanged",false).put("generationModel",model.getModel())
                .put("generationEndpoint",model.getEndpoint().toString()).put("embeddingModel",EmbeddingClient.MODEL)
                .put("embeddingEndpoint",EmbeddingClient.ENDPOINT);
        ObjectNode policy=raw.putObject("policy").put("candidateThreshold",.60).put("semanticThreshold",.73)
                .put("topK",3).put("maxDeepSeekCalls",maxGenerations).put("maxEmbeddingCalls",maxEmbeddings)
                .put("providerRetries",0).put("queryRewrites",0).put("calibrationCount",mainCount).put("diagnosticCount",diagnosticIdsToRun.size())
                .put("isIndependentAcceptance",false).put("requiresFactualReview",true);
        configurePolicy(policy);
        ObjectNode sources=policy.putObject("productionSourceSha256");
        for (String file:Arrays.asList("llm/LlmClient.java","retrieval/SemanticFaqRetriever.java","service/FaqService.java"))
            sources.put(file,sha(root().resolve("springboot/src/main/java/com/example/springboot/faq/"+file)));
        if (expectedSources.isEmpty()) {
            assertEquals("2f6ff6e3346fd23b858aa75461cdec4d9d384061609daa4c155acad54c25784e",sources.path("llm/LlmClient.java").asText());
            assertEquals("90e9f0ff8084278825991b8276c2c7456cb3b81574baa95f40c5f14ee803684b",sources.path("retrieval/SemanticFaqRetriever.java").asText());
            assertEquals("18ac7290b61fe70ba5ea4d0b7989b84008b330b9479075582c3a8fb2f24f486b",sources.path("service/FaqService.java").asText());
        } else for (Map.Entry<String,String> expected:expectedSources.entrySet())
            assertEquals(expected.getValue(),sources.path(expected.getKey()).asText());
        policy.put("priorDiagnosticReportSha256",PRIOR_SHA);
        save(output.resolve("frozen-policy.json"),policy);
        ArrayNode rows=raw.putArray("cases");
        ledger.put("version",raw.path("version").asText()).put("startedAt",Instant.now().toString())
                .put("deepSeekAttempts",0).put("embeddingAttempts",0).put("maxDeepSeekCalls",maxGenerations).put("maxEmbeddingCalls",maxEmbeddings);
        ObjectNode claimContent=ledger.deepCopy(); claimContent.set("policy",policy.deepCopy());
        claimAttempt(claim,claimContent);
        Files.write(output.resolve("live-attempt.json"),JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(ledger),StandardOpenOption.CREATE_NEW);
        installGuards();
        try {
            for (JsonNode item:cases) {
                activeCase=rows.addObject().put("id",item.path("id").asText()).put("question",item.path("question").asText())
                        .put("positive",item.path("expectedKeys").size()>0).put("diagnostic",item.path("diagnostic").asBoolean())
                        .put("providerFailed",false).put("retrievalAvailable",false).put("generationOutcome","not_called");
                activeCase.set("expectedKeys",item.path("expectedKeys").deepCopy());
                activeCase.putArray("retrievedKeys"); activeCase.putArray("retrievedCandidates");
                long started=System.nanoTime();
                try { recordAnswer(item,service.ask(item.path("question").asText())); }
                catch (RuntimeException error) {
                    activeCase.put("mode","unavailable").put("requestFailureType",error instanceof DataAccessException?"DATABASE_UNAVAILABLE":"REQUEST_UNAVAILABLE");
                } finally {
                    activeCase.put("elapsedMillis",(System.nanoTime()-started)/1000000); persist();
                }
                // An outage/cooldown would no longer measure this candidate policy. Never retry the case.
                if (!activeCase.path("retrievalAvailable").asBoolean()
                        || Arrays.asList("AUTHENTICATION","RATE_LIMITED").contains(activeCase.path("generationFailureType").asText())) break;
            }
            raw.put("completed",rows.size()==mainCount+diagnosticIdsToRun.size());
            verify(llm,never()).resolve(anyString(),anyList(),any());
        } finally {
            try { raw.put("databaseUnchanged",before.equals(jdbc.queryForList("SELECT * FROM faq_entry ORDER BY id"))); }
            catch (RuntimeException error) { raw.put("databaseVerificationFailed",true); }
            raw.put("finishedAt",Instant.now().toString()); persist();
            save(root().resolve("docs/faq-evaluation/"+runName+".json"),FaqRecallLiveReportTest.summarizeRecall(raw));
        }
        assertTrue(raw.path("databaseUnchanged").asBoolean()); assertEquals(mainCount+diagnosticIdsToRun.size(),rows.size(),"Partial measurement; do not automatically rerun");
        assertEquals(sampleSha,sha(root().resolve(sampleFile)));
        for (Iterator<Map.Entry<String,JsonNode>> it=sources.fields();it.hasNext();) {
            Map.Entry<String,JsonNode> field=it.next();
            assertEquals(field.getValue().asText(),sha(root().resolve("springboot/src/main/java/com/example/springboot/faq/"+field.getKey())));
        }
        assertTrue(generations<=maxGenerations && embeddings<=maxEmbeddings);
    }

    protected void configurePolicy(ObjectNode policy) { }

    private void validateCorpus(List<Map<String,Object>> before) throws Exception {
        JsonNode corpus=JSON.readTree(root().resolve("database/faq_ccnu_corpus_20261111.json").toFile());
        Map<String,JsonNode> reviewed=new HashMap<>();
        for (JsonNode row:corpus.path("entries")) assertNull(reviewed.put(row.path("question").asText(),row));
        List<FaqEntry> current=mapper.findEnabled();
        assertEquals(40,before.size()); assertEquals(40,current.size()); assertEquals(40,reviewed.size());
        for (FaqEntry row:current) {
            JsonNode expected=reviewed.get(row.getQuestion()); assertNotNull(expected);
            assertEquals(expected.path("answer").asText(),row.getAnswer()); assertEquals(expected.path("keywords").asText(),row.getKeywords());
            assertEquals(expected.path("category").asText(),row.getCategory()); assertEquals(expected.path("sourceTitle").asText(),row.getSourceTitle());
            assertEquals(corpus.path("sources").path(expected.path("sourceKey").asText()).path("url").asText(),row.getSourceUrl());
            assertEquals(Boolean.TRUE,row.getEnabled()); assertNull(keys.put(row.getId(),expected.path("key").asText()));
            entries.put(row.getId(),row); documentTexts.add(row.getQuestion()+" "+row.getKeywords());
        }
    }

    private void installGuards() {
        doAnswer(call->{
            List<String> texts=call.getArgument(0);
            for (String text:texts) assertTrue(documentTexts.contains(text)||text.equals(activeCase.path("question").asText()));
            if (embeddings>=maxEmbeddings) throw new AssertionError("Embedding call budget exhausted before outbound call");
            ledger.put("embeddingAttempts",++embeddings); save(output.resolve("live-attempt.json"),ledger);
            try { return call.callRealMethod(); }
            catch (RuntimeException error) { activeCase.put("providerFailed",true).put("embeddingFailureType","EMBEDDING_UNAVAILABLE"); throw error; }
        }).when(embedding).embed(anyList(),any());
        doAnswer(call->{
            assertEquals(activeCase.path("question").asText(),call.getArgument(0));
            if (!activeCase.path("retrievalAvailable").asBoolean()) {
                activeCase.put("generationOutcome","suppressed_after_retrieval_failure");
                throw new IllegalStateException("Measurement stopped after semantic retrieval failure");
            }
            List<FaqEntry> contexts=call.getArgument(1);
            assertTrue(!contexts.isEmpty()&&contexts.size()<=3);
            Set<Long> ids=new HashSet<>();
            for (FaqEntry context:contexts) {
                FaqEntry approved=entries.get(context.getId()); assertNotNull(approved);
                assertTrue(ids.add(context.getId()));
                assertEquals(approved.getQuestion(),context.getQuestion()); assertEquals(approved.getAnswer(),context.getAnswer());
                assertEquals(Boolean.TRUE,context.getEnabled());
            }
            if (generations>=maxGenerations) throw new AssertionError("Generation call budget exhausted before outbound call");
            ledger.put("deepSeekAttempts",++generations); save(output.resolve("live-attempt.json"),ledger);
            ArrayNode contextKeys=activeCase.putArray("generationContextKeys");
            for (FaqEntry context:contexts) { assertEquals(entries.get(context.getId()).getAnswer(),context.getAnswer()); contextKeys.add(keys.get(context.getId())); }
            try {
                LlmClient.Generation result=(LlmClient.Generation)call.callRealMethod();
                activeCase.put("generationOutcome",result.isInsufficientEvidence()?"insufficient_evidence":"generated"); return result;
            } catch (RuntimeException error) {
                activeCase.put("providerFailed",true).put("generationOutcome","failed").put("generationFailureType",
                        error instanceof LlmClient.LlmException?((LlmClient.LlmException)error).getType().name():
                                error instanceof FaqRequestBudget.ExpiredException?"REQUEST_DEADLINE":"UNEXPECTED_RUNTIME");
                if (error instanceof LlmClient.LlmException)
                    activeCase.put("validationReason",((LlmClient.LlmException)error).getValidationReason().name());
                throw error;
            }
        }).when(llm).generate(anyString(),anyList(),any());
        doAnswer(call->{throw new AssertionError("Single-turn scope forbids rewriting");}).when(llm).resolve(anyString(),anyList(),any());
        doAnswer(call->{
            SemanticFaqRetriever.Result result=(SemanticFaqRetriever.Result)call.callRealMethod();
            activeCase.put("retrievalAvailable",result.isAvailable());
            if (!result.isAvailable()) activeCase.put("semanticFailure","unavailable");
            for (TfIdfRetriever.Match hit:result.isAvailable()?result.getMatches():Collections.<TfIdfRetriever.Match>emptyList()) {
                activeCase.withArray("retrievedKeys").add(keys.get(hit.getEntry().getId()));
                activeCase.withArray("retrievedCandidates").addObject().put("key",keys.get(hit.getEntry().getId())).put("score",hit.getScore());
            }
            return result;
        }).when(semantic).searchCandidates(anyString(),anyList(),any());
    }

    private void recordAnswer(JsonNode item,FaqAnswerResponse answer) {
        activeCase.set("response",JSON.valueToTree(answer));
        activeCase.put("mode",answer.getMode()).put("answer",answer.getAnswer());
        ArrayNode actual=activeCase.putArray("sourceKeys"); Set<Long> sourceIds=new HashSet<>(); boolean valid=true,expected=false;
        for (FaqSource source:answer.getSources()) {
            FaqEntry row=entries.get(source.getFaqId());
            valid &= row!=null && Objects.equals(row.getSourceTitle(),source.getSourceTitle())
                    && Objects.equals(row.getSourceUrl(),source.getSourceUrl()) && sourceIds.add(source.getFaqId());
            actual.add(keys.get(source.getFaqId()));
            for (JsonNode key:item.path("expectedKeys")) expected |= key.asText().equals(keys.get(source.getFaqId()));
        }
        if ("generated".equals(answer.getMode())) {
            Set<Long> markers=new HashSet<>(); Matcher matcher=Pattern.compile("\\[FAQ:([1-9][0-9]*)\\]").matcher(answer.getAnswer());
            while (matcher.find()) markers.add(Long.parseLong(matcher.group(1)));
            valid &= !markers.isEmpty() && markers.equals(sourceIds) && answer.isMatched();
        } else if ("no_match".equals(answer.getMode())) valid &= sourceIds.isEmpty() && !answer.isMatched() && answer.getAnswer()==null;
        else valid=false; // Strict enabled-generation scope never expects an extractive answer.
        activeCase.put("expectedSourceReturned",expected).put("citationMetadataValid",valid);
        ArrayNode missing=activeCase.putArray("missingLiteralFacts");
        if ("generated".equals(answer.getMode())) for (JsonNode fact:item.path("mustPreserveFacts"))
            if (!Objects.toString(answer.getAnswer(),"").toLowerCase(Locale.ROOT).contains(fact.asText().toLowerCase(Locale.ROOT))) missing.add(fact);
        activeCase.put("literalFactChecksAreFactualProof",false);
    }

    private void persist() throws Exception {
        raw.put("deepSeekAttempts",generations).put("embeddingAttempts",embeddings); save(output.resolve("responses.json"),raw);
    }
    static void claimAttempt(Path claim,JsonNode value) throws Exception {
        Files.createDirectories(claim.getParent());
        Files.write(claim,JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(value),StandardOpenOption.CREATE_NEW);
    }
    static void validateLocalDataSource(DataSource source) throws Exception {
        assertTrue(source instanceof HikariDataSource,"Expected the pinned local datasource");
        String url=((HikariDataSource)source).getJdbcUrl();
        assertNotNull(url); assertTrue(url.startsWith("jdbc:mysql://localhost:3306/dormitory?"));
        URI uri=URI.create(url.substring(5)); assertEquals("localhost",uri.getHost());
        assertEquals(3306,uri.getPort()); assertEquals("/dormitory",uri.getPath());
    }
    private static void save(Path file,JsonNode value) throws Exception {
        Files.createDirectories(file.getParent()); Path temporary=file.resolveSibling(file.getFileName()+".tmp");
        Files.write(temporary,JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(value));
        Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING);
    }
}
