package com.example.springboot.faq;

import com.example.springboot.faq.config.*;
import com.example.springboot.faq.dto.FaqAnswerResponse;
import com.example.springboot.faq.entity.FaqEntry;
import com.example.springboot.faq.llm.LlmClient;
import com.example.springboot.faq.llm.ConversationTurn;
import com.example.springboot.faq.mapper.FaqMapper;
import com.example.springboot.faq.retrieval.*;
import com.example.springboot.faq.service.FaqService;
import com.example.springboot.faq.service.FaqRequestRunner;
import com.example.springboot.faq.support.FaqRequestBudget;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.dao.DataAccessResourceFailureException;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FaqSemanticRetrievalTest {
    private final FaqMapper mapper = mock(FaqMapper.class);
    private final EmbeddingClient embeddings = mock(EmbeddingClient.class);
    private final LlmClient llm = new LlmClient(new FaqLlmProperties(false, "", "", "", "max_tokens"),
            new ObjectMapper(), new RestTemplateBuilder());
    private final List<List<String>> calls = new ArrayList<>();

    private FaqService service() {
        when(embeddings.isEnabled()).thenReturn(true);
        when(embeddings.embed(anyList(), any())).thenAnswer(invocation -> {
            List<String> texts = invocation.getArgument(0);
            calls.add(new ArrayList<>(texts));
            double[][] result = new double[texts.size()][];
            for (int i = 0; i < texts.size(); i++) result[i] = vector(texts.get(i).endsWith("one") ? 0 : 1);
            return result;
        });
        return new FaqService(mapper, new TfIdfRetriever(), 0.35, llm, new SemanticFaqRetriever(embeddings));
    }

    @Test void actualAskAndContextPathsUseVectorOrderAndCurrentSources() {
        FaqService service = service();
        when(mapper.findEnabled()).thenReturn(Arrays.asList(entry(1, "one"), entry(2, "two")));
        FaqAnswerResponse answer = service.ask("alpha");
        assertEquals(2L, answer.getFaqId());
        assertEquals("answer 2", answer.getAnswer());
        assertEquals("source 2", answer.getSources().get(0).getSourceTitle());
        assertEquals(1.0, answer.getScore(), 1e-12);
        assertEquals(2L, service.retrieveContexts("alpha").get(0).getEntry().getId());
        assertEquals(Arrays.asList("alpha beta one", "alpha beta two"), calls.get(0));
        assertEquals(3, calls.size(), "one document build plus two current query requests");
    }

    @ParameterizedTest @ValueSource(strings = {"answer", "question", "keywords", "source", "url", "category", "time", "id"})
    void rebuildsOnEveryEvidenceRevision(String field) {
        FaqService service = service();
        FaqEntry row = entry(2, "two");
        when(mapper.findEnabled()).thenReturn(Collections.singletonList(row));
        service.ask("alpha");
        switch (field) {
            case "answer": row.setAnswer("new answer"); break;
            case "question": row.setQuestion("alpha new"); break;
            case "keywords": row.setKeywords("new keywords"); break;
            case "source": row.setSourceTitle("new source"); break;
            case "url": row.setSourceUrl("https://jwc.ccnu.edu.cn/new"); break;
            case "category": row.setCategory("new category"); break;
            case "time": row.setUpdatedAt(java.time.LocalDateTime.of(2026, 11, 17, 12, 0)); break;
            case "id": row.setId(3L); break;
            default: fail();
        }
        FaqAnswerResponse answer = service.ask("alpha");
        assertEquals(4, calls.size(), "changed content must rebuild documents and query");
        assertEquals(row.getAnswer(), answer.getAnswer());
        assertEquals(row.getSourceUrl(), answer.getSourceUrl());
        assertEquals(row.getId(), answer.getFaqId());
    }

    @Test void disablingDeletingAndInvalidSourcesNeverReuseOldVectors() {
        FaqService service = service();
        FaqEntry one = entry(1, "one"), two = entry(2, "two"), unsafe = entry(3, "two");
        unsafe.setSourceUrl("https://ccnu.edu.cn.evil.example/");
        when(mapper.findEnabled()).thenReturn(Arrays.asList(one, two, unsafe));
        assertEquals(2L, service.ask("alpha").getFaqId());
        assertEquals(2, calls.get(0).size());
        two.setEnabled(false);
        assertEquals(1L, service.ask("alpha").getFaqId());
        assertEquals(Collections.singletonList("alpha beta one"), calls.get(2));
        when(mapper.findEnabled()).thenReturn(Collections.emptyList());
        assertEquals("no_match", service.ask("alpha").getMode());
        assertEquals(4, calls.size());
    }

    @Test void noLexicalEvidenceStillRefusesWithoutCallingProvider() {
        FaqService service = service();
        when(mapper.findEnabled()).thenReturn(Collections.singletonList(entry(2, "two")));
        assertEquals("no_match", service.ask("volcano eruption").getMode());
        assertTrue(calls.isEmpty());
    }

    @Test void disabledEmbeddingsAndDatabaseErrorsPreserveOriginalBehavior() {
        FaqService service = service();
        when(embeddings.isEnabled()).thenReturn(false);
        when(mapper.findEnabled()).thenReturn(Arrays.asList(entry(1, "one"), entry(2, "two")));
        assertEquals(1L, service.ask("alpha").getFaqId());
        assertTrue(calls.isEmpty());
        when(mapper.findEnabled()).thenThrow(new DataAccessResourceFailureException("private"));
        assertThrows(DataAccessResourceFailureException.class, () -> service.ask("alpha"));
    }

    @Test void failedBuildFallsBackAndCooldownAvoidsRepeatedProviderCalls() {
        FaqService service = service();
        when(mapper.findEnabled()).thenReturn(Arrays.asList(entry(1, "one"), entry(2, "two")));
        when(embeddings.embed(anyList(), any())).thenThrow(new IllegalArgumentException("provider secret"));
        assertEquals(1L, service.ask("alpha").getFaqId());
        assertEquals(1L, service.ask("alpha").getFaqId());
        verify(embeddings, times(1)).embed(anyList(), any());
    }

    @Test void failedChangedSnapshotNeverUsesOldVectorsOrOldAnswer() {
        FaqService service = service();
        FaqEntry one = entry(1, "one"), two = entry(2, "two");
        when(mapper.findEnabled()).thenReturn(Arrays.asList(one, two));
        assertEquals(2L, service.ask("alpha").getFaqId());
        one.setAnswer("changed current answer");
        when(embeddings.embed(anyList(), any())).thenThrow(new IllegalArgumentException("offline"));
        assertEquals("changed current answer", service.ask("alpha").getAnswer());
        assertEquals(1L, service.ask("alpha").getFaqId());
    }

    @Test void competingBuildNeverWaitsAndCancelledBuildIsNotPublished() throws Exception {
        FaqService service = service();
        when(mapper.findEnabled()).thenReturn(Collections.singletonList(entry(2, "two")));
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        doAnswer(invocation -> {
            entered.countDown(); assertTrue(release.await(2, TimeUnit.SECONDS));
            return new double[][]{vector(1)};
        }).when(embeddings).embed(anyList(), any());
        ExecutorService worker = Executors.newSingleThreadExecutor();
        FaqRequestBudget budget = new FaqRequestBudget(35000);
        try {
            Future<?> pending = worker.submit(() -> service.ask("alpha", budget));
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            assertTimeoutPreemptively(java.time.Duration.ofMillis(500), () -> assertEquals(2L, service.ask("alpha").getFaqId()));
            budget.cancel(); release.countDown();
            assertThrows(ExecutionException.class, () -> pending.get(2, TimeUnit.SECONDS));
        } finally { release.countDown(); worker.shutdownNow(); }
    }

    @Test void excessiveCorpusFallsBackBeforeAnyProviderRequest() {
        FaqService service = service();
        List<FaqEntry> rows = new ArrayList<>();
        for (int i = 1; i <= 129; i++) rows.add(entry(i, "two"));
        when(mapper.findEnabled()).thenReturn(rows);
        assertEquals(1L, service.ask("alpha").getFaqId());
        assertTrue(calls.isEmpty());
    }

    @Test void tiedVectorsAreStableAndLimitAppliesAfterRanking() {
        FaqService service = service();
        when(mapper.findEnabled()).thenReturn(Arrays.asList(entry(9, "two"), entry(3, "two"), entry(5, "two"), entry(2, "two")));
        assertEquals(Arrays.asList(2L, 3L, 5L), service.retrieveContexts("alpha").stream()
                .map(m -> m.getEntry().getId()).collect(Collectors.toList()));
    }

    @Test void partialRebuildIsDiscardedAndBatchLimitsAreRespected() {
        FaqService service = service();
        List<FaqEntry> rows = new ArrayList<>();
        for (int i = 1; i <= 17; i++) rows.add(entry(i, "two"));
        when(mapper.findEnabled()).thenReturn(rows);
        doAnswer(invocation -> {
            List<String> batch = invocation.getArgument(0);
            calls.add(new ArrayList<>(batch));
            if (calls.size() == 2) throw new IllegalArgumentException("second batch offline");
            double[][] vectors = new double[batch.size()][];
            for (int i = 0; i < vectors.length; i++) vectors[i] = vector(1);
            return vectors;
        }).when(embeddings).embed(anyList(), any());
        assertEquals(1L, service.ask("alpha").getFaqId());
        assertEquals(Arrays.asList(16, 1), calls.stream().map(List::size).collect(Collectors.toList()));
        assertEquals(1L, service.ask("alpha").getFaqId());
        assertEquals(2, calls.size(), "cooldown cannot use a partially built index");
    }

    @Test void queryFailureAfterSuccessfulBuildFallsBackToLexicalOrder() {
        FaqService service = service();
        when(mapper.findEnabled()).thenReturn(Arrays.asList(entry(1, "one"), entry(2, "two")));
        doAnswer(invocation -> {
            List<String> batch = invocation.getArgument(0);
            if (batch.size() == 1) throw new IllegalArgumentException("query failed");
            return new double[][]{vector(0), vector(1)};
        }).when(embeddings).embed(anyList(), any());
        assertEquals(1L, service.ask("alpha").getFaqId());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void parentDeadlineDuringEmbeddingRetainsCurrentStoredAnswerAndResolvedTopic(boolean followUp) {
        service();
        when(mapper.findEnabled()).thenReturn(Collections.singletonList(entry(2, "two")));
        LlmClient resolving = llm;
        if (followUp) {
            resolving = new LlmClient(new FaqLlmProperties(true, "https://model.example/v1", "fixture-key", "fixture-model", "max_tokens"),
                    new ObjectMapper(), new RestTemplateBuilder().additionalCustomizers(template -> {
                org.springframework.test.web.client.MockRestServiceServer server = org.springframework.test.web.client.MockRestServiceServer.bindTo(template).build();
                server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.anything()).andRespond(
                        org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess(
                                "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"role\":\"assistant\",\"content\":\"{\\\"action\\\":\\\"follow_up\\\",\\\"question\\\":\\\"alpha\\\"}\"}}]}",
                                org.springframework.http.MediaType.APPLICATION_JSON));
            }));
        }
        doAnswer(invocation -> {
            try { Thread.sleep(2000); } catch (InterruptedException expected) { Thread.currentThread().interrupt(); }
            return new double[][]{vector(1)};
        }).when(embeddings).embed(anyList(), any());
        FaqService subject = new FaqService(mapper, new TfIdfRetriever(), 0.35, resolving, new SemanticFaqRetriever(embeddings));
        FaqRequestRunner runner = new FaqRequestRunner(subject, 300);
        try {
            if (followUp) {
                FaqAnswerResponse prior = new FaqService(mapper, new TfIdfRetriever(), 0.35, llm).ask("alpha");
                FaqService.ChatAnswer result = runner.chat("What about it?",
                        Collections.singletonList(new ConversationTurn("alpha", prior)), new FaqRequestBudget(300));
                assertEquals("extractive", result.getAnswer().getMode());
                assertEquals("answer 2", result.getAnswer().getAnswer());
                assertEquals("alpha", result.getQuestion());
            } else {
                assertEquals("answer 2", runner.ask("alpha").getAnswer());
            }
        } finally { runner.close(); }
    }

    private static double[] vector(int direction) { double[] v = new double[1024]; v[direction] = 1; return v; }
    private static FaqEntry entry(long id, String keywords) {
        FaqEntry row = new FaqEntry(); row.setId(id); row.setQuestion("alpha beta");
        row.setKeywords(keywords); row.setAnswer("answer " + id); row.setEnabled(true);
        row.setSourceTitle("source " + id); row.setSourceUrl("https://jwc.ccnu.edu.cn/" + id);
        return row;
    }
}
