package com.example.springboot.faq;

import com.example.springboot.faq.config.*;
import com.example.springboot.faq.llm.LlmClient;
import com.example.springboot.faq.entity.FaqEntry;
import com.example.springboot.faq.retrieval.*;
import com.example.springboot.faq.support.FaqRequestBudget;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.file.*;
import java.util.Collections;
import java.util.Map;
import static com.example.springboot.faq.FaqStep6AcceptanceReportTest.JSON;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.spy;

/** Budget guards exercise disabled real clients: these checks cannot reach the network. */
class FaqRecallLiveSafetyTest {
    @TempDir Path output;
    private FaqRecallLiveIT fixture() {
        FaqRecallLiveIT it=new FaqRecallLiveIT();
        it.llm=spy(new LlmClient(new FaqLlmProperties(false,"","","","max_tokens"),JSON,new RestTemplateBuilder()));
        it.embedding=spy(new EmbeddingClient(new FaqEmbeddingProperties(false,""),JSON));
        it.semantic=spy(new SemanticFaqRetriever(it.embedding));
        ReflectionTestUtils.setField(it,"output",output);
        ReflectionTestUtils.setField(it,"activeCase",JSON.createObjectNode().put("question","public query").put("retrievalAvailable",true));
        Map<Long,FaqEntry> entries=(Map<Long,FaqEntry>)ReflectionTestUtils.getField(it,"entries"); entries.put(1L,evidence());
        ReflectionTestUtils.invokeMethod(it,"installGuards");
        return it;
    }
    private FaqEntry evidence() {
        FaqEntry row=new FaqEntry(); row.setId(1L); row.setQuestion("public evidence"); row.setAnswer("public answer"); row.setEnabled(true); return row;
    }
    @Test void exhaustedGenerationBudgetStopsBeforeClientAndBeforeLedgerChange() {
        FaqRecallLiveIT it=fixture(); ReflectionTestUtils.setField(it,"generations",34);
        AssertionError error=assertThrows(AssertionError.class,()->it.llm.generate("public query",Collections.singletonList(evidence()),new FaqRequestBudget(35000)));
        assertTrue(error.getMessage().contains("budget exhausted"));
        assertFalse(Files.exists(output.resolve("live-attempt.json")));
    }
    @Test void exhaustedEmbeddingBudgetStopsBeforeClientAndBeforeLedgerChange() {
        FaqRecallLiveIT it=fixture(); ReflectionTestUtils.setField(it,"embeddings",37);
        assertThrows(AssertionError.class,()->it.embedding.embed(Collections.singletonList("public query"),new FaqRequestBudget(35000)));
        assertFalse(Files.exists(output.resolve("live-attempt.json")));
    }
    @Test void failedClientAttemptIsChargedToPersistedBudgetAndHasOnlySafeErrorType() throws Exception {
        FaqRecallLiveIT it=fixture();
        LlmClient.LlmException failure=assertThrows(LlmClient.LlmException.class,
                ()->it.llm.generate("public query",Collections.singletonList(evidence()),new FaqRequestBudget(35000)));
        assertEquals(LlmClient.Type.DISABLED,failure.getType());
        assertEquals(1,JSON.readTree(output.resolve("live-attempt.json").toFile()).path("deepSeekAttempts").asInt());
        ObjectNode row=(ObjectNode)ReflectionTestUtils.getField(it,"activeCase");
        assertEquals("DISABLED",row.path("generationFailureType").asText());
        assertTrue(row.path("providerFailed").asBoolean()); assertFalse(row.has("exceptionMessage"));
    }
    @Test void unapprovedEmbeddingTextStopsBeforeBudgetOrNetwork() {
        FaqRecallLiveIT it=fixture();
        assertThrows(AssertionError.class,()->it.embedding.embed(Collections.singletonList("unapproved payload"),new FaqRequestBudget(35000)));
        assertFalse(Files.exists(output.resolve("live-attempt.json")));
    }

    @Test void changedContextQuestionStopsBeforeBudgetOrNetwork() {
        FaqRecallLiveIT it=fixture(); FaqEntry changed=evidence(); changed.setQuestion("unapproved question");
        assertThrows(AssertionError.class,()->it.llm.generate("public query",Collections.singletonList(changed),new FaqRequestBudget(35000)));
        assertFalse(Files.exists(output.resolve("live-attempt.json")));
    }

    @Test void unavailableRetrievalCannotReserveGeneration() {
        FaqRecallLiveIT it=fixture();
        ((ObjectNode)ReflectionTestUtils.getField(it,"activeCase")).put("retrievalAvailable",false);
        assertThrows(IllegalStateException.class,()->it.llm.generate("public query",Collections.singletonList(evidence()),new FaqRequestBudget(35000)));
        assertFalse(Files.exists(output.resolve("live-attempt.json")));
    }

    @Test void immutableClaimSurvivesLossOfMutableBuildLedger() throws Exception {
        Path claim=output.resolve("durable-claim.json"), mutable=output.resolve("build-counter.json");
        ObjectNode value=JSON.createObjectNode().put("run","original");
        FaqRecallLiveIT.claimAttempt(claim,value);
        Files.write(mutable,new byte[]{1}); Files.delete(mutable);
        assertThrows(FileAlreadyExistsException.class,()->FaqRecallLiveIT.claimAttempt(claim,JSON.createObjectNode().put("run","second")));
        assertEquals("original",JSON.readTree(claim.toFile()).path("run").asText());
    }

    @ParameterizedTest @ValueSource(strings={"jdbc:mysql://remote.invalid:3306/dormitory?x=y",
            "jdbc:mysql://localhost:3307/dormitory?x=y","jdbc:mysql://localhost:3306/other?x=y"})
    void datasourceIsRejectedBeforeAnyConnectionWhenTargetIsNotPinned(String url) {
        try (com.zaxxer.hikari.HikariDataSource source=new com.zaxxer.hikari.HikariDataSource()) {
            source.setJdbcUrl(url);
            assertThrows(AssertionError.class,()->FaqRecallLiveIT.validateLocalDataSource(source));
            assertFalse(source.isRunning());
        }
    }
}
