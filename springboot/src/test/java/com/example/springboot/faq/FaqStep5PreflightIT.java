package com.example.springboot.faq;

import com.example.springboot.faq.entity.FaqEntry;
import com.example.springboot.faq.mapper.FaqMapper;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Local-only payload classification, no credentials for or calls to any model service. */
@EnabledIfEnvironmentVariable(named = "FAQ_STEP5_DB_VERIFY", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.main.banner-mode=off", "logging.level.root=WARN", "faq.embedding.enabled=false",
        "faq.llm.enabled=false", "faq.deepseek.enabled=false"})
class FaqStep5PreflightIT {
    @Autowired FaqMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;

    @Test void allOutboundDocumentTextsAreExactlyFrozenPublicKnowledge() throws Exception {
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
        List<String> outbound = new ArrayList<>();
        for (FaqEntry row : entries) {
            JsonNode expected = reviewed.get(row.getQuestion()); assertNotNull(expected);
            assertEquals(expected.path("keywords").asText(), row.getKeywords());
            assertEquals(expected.path("answer").asText(), row.getAnswer());
            JsonNode source = corpus.path("sources").path(expected.path("sourceKey").asText());
            assertEquals(source.path("url").asText(), row.getSourceUrl());
            String host = URI.create(row.getSourceUrl()).getHost();
            assertTrue("ccnu.edu.cn".equals(host) || host.endsWith(".ccnu.edu.cn"));
            assertTrue(Boolean.TRUE.equals(row.getEnabled()));
            outbound.add(row.getQuestion() + " " + row.getKeywords());
        }
        assertEquals(before, jdbc.queryForList("SELECT * FROM faq_entry ORDER BY id"));
        byte[] bytes = json.writeValueAsBytes(outbound);
        ObjectNode report = json.createObjectNode().put("date", "2026-11-17").put("documents", entries.size())
                .put("allOutboundTextsEqualFrozenPublicCorpus", true).put("officialSourcesMatched", true)
                .put("accountTablesRead", false).put("providerCalls", 0).put("databaseWrites", 0)
                .put("databaseUnchanged", true).put("payloadFields", "public question + space + public keywords")
                .put("inputTextBytes", outbound.stream().mapToInt(s -> s.getBytes(StandardCharsets.UTF_8).length).sum())
                .put("inputJsonSha256", Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(bytes)));
        Path output = root.resolve("springboot/target/faq-step5/public-payload-preflight.json");
        Files.createDirectories(output.getParent());
        json.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), report);
    }
}
