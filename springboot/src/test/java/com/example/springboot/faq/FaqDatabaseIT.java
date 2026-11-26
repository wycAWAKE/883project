package com.example.springboot.faq;

import com.example.springboot.faq.dto.FaqAnswerResponse;
import com.example.springboot.faq.entity.FaqEntry;
import com.example.springboot.faq.mapper.FaqMapper;
import com.example.springboot.faq.service.FaqService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.net.URI;
import java.sql.Connection;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/** Explicit opt-in only: FAQ_DB_VERIFY=true mvn -Dtest=FaqDatabaseIT test. */
@EnabledIfEnvironmentVariable(named = "FAQ_DB_VERIFY", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"spring.main.banner-mode=off", "logging.level.root=WARN", "faq.llm.enabled=false", "faq.deepseek.enabled=false"})
class FaqDatabaseIT {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private FaqMapper mapper;
    @Autowired private FaqService service;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private DataSource dataSource;

    @Test
    void realMapperFiltersDisabledRowsAndServiceSeesUpdatesThenEverythingRollsBack() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            URI target = new URI(connection.getMetaData().getURL().substring("jdbc:".length()));
            assertTrue("localhost".equals(target.getHost()) || "127.0.0.1".equals(target.getHost()),
                    "Integration verification is restricted to the approved local database");
            assertEquals(3306, target.getPort());
        }
        assertEquals("dormitory", jdbc.queryForObject("SELECT DATABASE()", String.class));
        assertEquals("InnoDB", jdbc.queryForObject("SELECT ENGINE FROM information_schema.TABLES "
                + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='faq_entry'", String.class));
        List<Map<String, Object>> before = snapshot();
        List<FaqEntry> original = mapper.findEnabled();
        assertTrue(before.size() == 5 || before.size() == 40, "Preflight requires the approved initial or expanded corpus");
        assertEquals(before.size(), original.size(), "All reviewed corpus rows must be enabled");
        verifyReviewedCorpus(original);
        assertEquals(Arrays.asList(1L, 2L, 3L, 4L, 5L), original.stream()
                .limit(5).map(FaqEntry::getId).collect(Collectors.toList()));
        assertEquals("Where can I find the CCNU academic calendar for 2026-2027?", original.get(0).getQuestion());
        String tableNames = jdbc.queryForList("SHOW TABLES").toString();
        Long nextId = nextId();
        try {
            for (FaqEntry entry : original) {
                FaqAnswerResponse response = service.ask(entry.getQuestion());
                assertEquals(entry.getId(), response.getFaqId());
                assertEquals(entry.getAnswer(), response.getAnswer());
                assertEquals(entry.getSourceUrl(), response.getSourceUrl());
                assertEquals(entry.getId(), service.retrieveContexts(entry.getQuestion()).get(0).getEntry().getId());
            }
            TransactionTemplate transaction = new TransactionTemplate(transactionManager);
            transaction.execute(status -> {
                status.setRollbackOnly();
                FaqEntry change = new FaqEntry();
                change.setId(1L);
                change.setEnabled(false);
                assertEquals(1, mapper.updateById(change));
                assertEquals(original.size() - 1, mapper.findEnabled().size());
                assertFalse(mapper.findEnabled().stream().anyMatch(entry -> entry.getId().equals(1L)));
                assertNotEquals(1L, service.ask(original.get(0).getQuestion()).getFaqId());
                assertFalse(service.retrieveContexts(original.get(0).getQuestion()).stream()
                        .anyMatch(match -> match.getEntry().getId().equals(1L)));

                // Reuse a known row only inside this rollback transaction. No insert, DDL or sequence changes.
                String probeQuestion = "r1transactionfixtureknowledge";
                change.setEnabled(true);
                change.setQuestion(probeQuestion);
                change.setKeywords(probeQuestion);
                change.setAnswer("R1 transactional fixture version one; not campus policy.");
                assertEquals(1, mapper.updateById(change));
                assertEquals(change.getAnswer(), service.ask(probeQuestion).getAnswer());
                assertEquals(1L, service.ask(probeQuestion).getFaqId());
                change.setAnswer("R1 transactional fixture version two; not campus policy.");
                assertEquals(1, mapper.updateById(change));
                assertEquals(change.getAnswer(), service.ask(probeQuestion).getAnswer());
                assertEquals(change.getAnswer(), service.retrieveContexts(probeQuestion).get(0).getEntry().getAnswer());
                return null;
            });
        } finally {
            assertEquals(before, snapshot(), "All corpus fields, enabled flags and timestamps must be restored by rollback");
            assertEquals(tableNames, jdbc.queryForList("SHOW TABLES").toString());
            assertEquals(nextId, nextId(), "Verification must not consume auto-increment IDs");
        }
        System.out.println("PASS: real MySQL Mapper/Service read all " + original.size() + " reviewed FAQs and their Top-K contexts.");
        System.out.println("PASS: disabled FAQ excluded; same Service instance saw two answer versions without rebuilding.");
        System.out.println("PASS: transaction rolled back; all rows, IDs, flags, timestamps, table names and ID sequence unchanged.");
    }

    private void verifyReviewedCorpus(List<FaqEntry> rows) throws Exception {
        JsonNode corpus = new ObjectMapper().readTree(Paths.get(System.getProperty("basedir", "."))
                .toAbsolutePath().getParent().resolve("database/faq_ccnu_corpus_20261111.json").toFile());
        assertTrue(rows.size() == 5 || rows.size() == 40);
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (FaqEntry row : rows) {
            assertTrue(seen.add(row.getQuestion()), "Duplicate reviewed questions must not pass preflight");
            JsonNode expected = null;
            for (JsonNode candidate : corpus.path("entries")) {
                if (candidate.path("question").asText().equals(row.getQuestion())) expected = candidate;
            }
            assertNotNull(expected, "Unexpected FAQ must not be modified by this opt-in test");
            assertEquals(expected.path("answer").asText(), row.getAnswer());
            assertEquals(expected.path("category").asText(), row.getCategory());
            assertEquals(expected.path("keywords").asText(), row.getKeywords());
            assertEquals(expected.path("sourceTitle").asText(), row.getSourceTitle());
            assertEquals(corpus.path("sources").path(expected.path("sourceKey").asText()).path("url").asText(), row.getSourceUrl());
        }
    }

    private List<Map<String, Object>> snapshot() {
        return jdbc.queryForList("SELECT id, question, answer, category, keywords, source_title, source_url, "
                + "enabled, updated_at FROM faq_entry ORDER BY id");
    }

    private Long nextId() {
        return jdbc.queryForObject("SELECT AUTO_INCREMENT FROM information_schema.TABLES "
                + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='faq_entry'", Long.class);
    }
}
