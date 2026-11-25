package com.example.springboot.faq;

import com.example.springboot.faq.config.FaqLlmProperties;
import com.example.springboot.faq.entity.FaqEntry;
import com.example.springboot.faq.llm.LlmClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.web.client.RestTemplateBuilder;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static com.example.springboot.faq.FaqStep6AcceptanceReportTest.*;
import static org.junit.jupiter.api.Assertions.*;

/** Four public-evidence completions only: no Spring context, SQL, embeddings, rewrites or retries. */
@EnabledIfEnvironmentVariable(named="FAQ_OUTPUT_DIAGNOSTICS_VERIFY", matches="true")
class FaqOutputDiagnosticsIT {
    @Test void classifyFourPreviouslyRejectedOutputs() throws Exception {
        assertEquals("4:0",System.getenv("FAQ_OUTPUT_DIAGNOSTICS_BUDGET"));
        String key=System.getenv("DEEPSEEK_API_KEY"); assertNotNull(key); assertFalse(key.trim().isEmpty());
        assertEquals(CORPUS_SHA,sha(root().resolve("database/faq_ccnu_corpus_20261111.json")));
        Path previous=root().resolve("springboot/target/faq-recall-live-20261122/responses.json");
        assertEquals("fe0ab5f3e0ac812bb60c0569b63b6946e860336bee8982b50cf8b306fefe21c9",sha(previous));
        Map<String,FaqEntry> entries=new HashMap<>();
        for (JsonNode row:JSON.readTree(root().resolve("database/faq_ccnu_corpus_20261111.json").toFile()).path("entries")) {
            FaqEntry entry=new FaqEntry();
            entry.setId(Long.parseLong(row.path("key").asText().substring("CCNU-FAQ-".length())));
            entry.setQuestion(row.path("question").asText()); entry.setAnswer(row.path("answer").asText()); entry.setEnabled(true);
            entries.put(row.path("key").asText(),entry);
        }
        ObjectNode result=JSON.createObjectNode().put("startedAt",Instant.now().toString())
                .put("maximumDeepSeekAttempts",4).put("deepSeekAttempts",0).put("embeddingAttempts",0)
                .put("databaseAccess",false).put("retries",0).put("syntheticCitationIds",true)
                .put("sourceSha256",sha(root().resolve("springboot/src/main/java/com/example/springboot/faq/llm/LlmClient.java")));
        ArrayNode cases=result.putArray("cases");
        List<JsonNode> selected=new ArrayList<>();
        for (JsonNode row:JSON.readTree(previous.toFile()).path("cases"))
            if (Arrays.asList("R08P","R10P","H22","H26").contains(row.path("id").asText())) selected.add(row);
        assertEquals(4,selected.size());
        for (JsonNode row:selected) {
            assertEquals("INVALID_RESPONSE",row.path("generationFailureType").asText());
            assertTrue(row.path("generationContextKeys").size()>0&&row.path("generationContextKeys").size()<=3);
            for (JsonNode context:row.path("generationContextKeys")) assertNotNull(entries.get(context.asText()));
        }
        Path claim=root().resolve("docs/faq-evaluation/output-diagnostics-20261122-claim.json");
        Path report=root().resolve("docs/faq-evaluation/output-diagnostics-20261122.json");
        assertFalse(Files.exists(report));
        FaqRecallLiveIT.claimAttempt(claim,result);
        LlmClient client=new LlmClient(new FaqLlmProperties(true,"https://api.deepseek.com",key,
                "deepseek-flash","max_tokens"),JSON,new RestTemplateBuilder());
        int attempts=0;
        for (JsonNode prior:selected) {
            assertTrue(attempts<4);
            List<FaqEntry> contexts=new ArrayList<>();
            for (JsonNode context:prior.path("generationContextKeys")) contexts.add(entries.get(context.asText()));
            ObjectNode row=cases.addObject().put("id",prior.path("id").asText()).put("question",prior.path("question").asText());
            row.set("contextKeys",prior.path("generationContextKeys").deepCopy());
            result.put("deepSeekAttempts",++attempts);
            save(report,result); // Count failures as attempts before the outbound call.
            try {
                LlmClient.Generation answer=client.generate(row.path("question").asText(),contexts);
                row.put("outcome",answer.isInsufficientEvidence()?"insufficient_evidence":"generated")
                        .put("answer",answer.getAnswer()).set("citedFaqIds",JSON.valueToTree(answer.getCitedFaqIds()));
            } catch (LlmClient.LlmException error) {
                row.put("outcome","failed").put("failureType",error.getType().name())
                        .put("validationReason",error.getValidationReason().name());
                save(report,result);
                if (error.getType()==LlmClient.Type.AUTHENTICATION||error.getType()==LlmClient.Type.RATE_LIMITED) break;
            } finally { result.put("finishedAt",Instant.now().toString()); save(report,result); }
        }
        assertTrue(attempts<=4);
    }
    private void save(Path path,JsonNode result) throws Exception {
        Path temporary=path.resolveSibling(path.getFileName()+".tmp");
        Files.write(temporary,JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(result));
        Files.move(temporary,path,StandardCopyOption.REPLACE_EXISTING);
    }
}
