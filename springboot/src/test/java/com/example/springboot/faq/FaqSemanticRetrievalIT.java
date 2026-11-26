package com.example.springboot.faq;

import com.example.springboot.faq.dto.FaqAnswerResponse;
import com.example.springboot.faq.entity.FaqEntry;
import com.example.springboot.faq.llm.LlmClient;
import com.example.springboot.faq.mapper.FaqMapper;
import com.example.springboot.faq.retrieval.*;
import com.example.springboot.faq.service.FaqService;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import javax.sql.DataSource;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Explicit read-only local-MySQL + selected-free-embedding acceptance; never calls chat generation. */
@EnabledIfEnvironmentVariable(named = "FAQ_STEP5_LIVE_VERIFY", matches = "true")
@ActiveProfiles("faq-embedding")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.main.banner-mode=off", "logging.level.root=WARN", "faq.llm.enabled=false", "faq.deepseek.enabled=false"})
class FaqSemanticRetrievalIT {
    @Autowired FaqMapper mapper;
    @Autowired FaqService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @SpyBean EmbeddingClient client;
    @SpyBean LlmClient llm;

    @Test void realServiceUsesReusedBgeIndexAndLeavesDatabaseUnchanged() throws Exception {
        try (java.sql.Connection connection = dataSource.getConnection()) {
            URI target = URI.create(connection.getMetaData().getURL().substring(5));
            assertTrue("localhost".equals(target.getHost()) || "127.0.0.1".equals(target.getHost()));
            assertEquals(3306, target.getPort());
        }
        assertEquals("dormitory", jdbc.queryForObject("SELECT DATABASE()", String.class));
        List<Map<String, Object>> before = jdbc.queryForList("SELECT * FROM faq_entry ORDER BY id");
        List<FaqEntry> entries = mapper.findEnabled();
        assertEquals(40, before.size()); assertEquals(40, entries.size());
        Path root = Paths.get(System.getProperty("basedir", ".")).toAbsolutePath().getParent();
        ObjectMapper json = new ObjectMapper();
        JsonNode corpus = json.readTree(root.resolve("database/faq_ccnu_corpus_20261111.json").toFile());
        Map<String, JsonNode> reviewed = new HashMap<>();
        for (JsonNode row : corpus.path("entries")) reviewed.put(row.path("question").asText(), row);
        for (FaqEntry row : entries) {
            JsonNode expected = reviewed.get(row.getQuestion());
            assertNotNull(expected, "Only the reviewed public corpus may be sent");
            assertEquals(expected.path("answer").asText(), row.getAnswer());
            assertEquals(expected.path("keywords").asText(), row.getKeywords());
            JsonNode source = corpus.path("sources").path(expected.path("sourceKey").asText());
            assertEquals(source.path("url").asText(), row.getSourceUrl());
        }
        AtomicInteger requests = new AtomicInteger();
        Map<String, double[]> vectors = new HashMap<>();
        doAnswer(invocation -> {
            if (requests.incrementAndGet() > 8) throw new IllegalStateException("Step5 verification request cap reached.");
            List<String> texts = invocation.getArgument(0);
            double[][] result = (double[][]) invocation.callRealMethod();
            for (int i = 0; i < texts.size(); i++) vectors.put(texts.get(i), result[i]);
            return result;
        }).when(client).embed(anyList(), any());
        String question = "Where can I find the CCNU academic calendar for 2026-2027?";
        ObjectNode report = json.createObjectNode().put("date", "2026-11-17").put("model", EmbeddingClient.MODEL)
                .put("dimensions", EmbeddingClient.DIMENSIONS).put("generationCalls", 0).put("databaseWrites", 0)
                .put("acceptancePolicy", "existing TF-IDF >= 0.35; BGE ranking; step6 uncalibrated");
        boolean success = false;
        try {
            FaqAnswerResponse first = service.ask(question);
            assertEquals(4, requests.get(), "three document batches and one query");
            assertEquals(41, vectors.size());
            List<TfIdfRetriever.Match> admitted = new TfIdfRetriever().findTopMatches(question, entries, entries.size()).stream()
                    .filter(m -> m.getScore() >= 0.35).collect(Collectors.toList());
            FaqEntry best = null; double bestScore = -1;
            for (TfIdfRetriever.Match candidate : admitted) {
                FaqEntry row = candidate.getEntry();
                double[] left = vectors.get(question), right = vectors.get(row.getQuestion() + " " + row.getKeywords());
                double score = 0; for (int i = 0; i < 1024; i++) score += left[i] * right[i];
                if (score > bestScore || (score == bestScore && row.getId() < best.getId())) { best = row; bestScore = score; }
            }
            assertNotNull(best); assertEquals(best.getId(), first.getFaqId());
            assertEquals(bestScore, first.getScore(), 1e-12);
            assertEquals(best.getSourceUrl(), first.getSourceUrl());
            assertEquals("extractive", first.getMode());
            assertEquals(first.getFaqId(), service.ask(question).getFaqId());
            assertEquals(5, requests.get(), "second ask reuses the document index");
            assertEquals("no_match", service.ask("volcano eruption").getMode());
            assertEquals(5, requests.get(), "no-evidence queries do not call the provider");
            verify(llm, never()).generate(anyString(), anyList(), any());
            assertEquals(before, jdbc.queryForList("SELECT * FROM faq_entry ORDER BY id"));
            report.put("faqId", first.getFaqId()).put("score", first.getScore()).put("indexReused", true)
                    .put("databaseUnchanged", true).put("noMatchPassed", true);
            success = true;
        } finally {
            report.put("requests", requests.get()).put("passed", success);
            Path output = root.resolve("springboot/target/faq-step5/live-result.json");
            Files.createDirectories(output.getParent());
            json.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), report);
        }
    }
}
