package com.example.springboot.m2;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

class PublicCatalogEvaluationTest {
    @Test
    void twentyFrozenSyntheticProfilesHaveNoEligibilityViolations() throws Exception {
        ObjectMapper json = new ObjectMapper();
        JsonNode data = json.readTree(Paths.get("../datasets/mit-ocw/catalog.json").toFile());
        List<Course> catalog = new ArrayList<>();
        for (JsonNode n : data.get("courses")) {
            Course c = new Course();
            c.setCode(n.get("code").asText());
            c.setTitle(n.get("title").asText());
            c.setDescription(n.get("description").asText());
            c.setTopics(n.get("topics").toString());
            c.setPrerequisiteStatus(n.get("prerequisiteStatus").asText());
            c.setActive(true);
            List<List<String>> groups = new ArrayList<>();
            for (JsonNode group : n.get("prerequisiteGroups")) {
                List<String> g = new ArrayList<>();
                for (JsonNode p : group) g.add(p.asText());
                groups.add(g);
            }
            c.setPrerequisiteGroups(groups);
            catalog.add(c);
        }
        Recommender rules = new Recommender();
        Course algorithms =
                catalog.stream().filter(c -> c.getCode().equals("6.006")).findFirst().get();
        assertFalse(
                rules.eligible(algorithms, new HashSet<>(Arrays.asList("6.0001", "6.042J"))),
                "Mandatory assessment needs staff review");
        Course advanced =
                catalog.stream().filter(c -> c.getCode().equals("6.046J")).findFirst().get();
        assertTrue(
                rules.eligible(advanced, new HashSet<>(Arrays.asList("6.006", "18.062J"))),
                "Explicit alternative identifier must work");
        List<String> rows = new ArrayList<>();
        rows.add(
                "case,tfidf_codes,keyword_codes,tfidf_hit_at_3,keyword_hit_at_3,tfidf_precision_at_3,keyword_precision_at_3,eligibility_violations");
        Recommender ranker = new Recommender();
        int hits = 0, baseHits = 0, count = 0;
        double precision = 0, basePrecision = 0;
        for (JsonNode p :
                json.readTree(Paths.get("../datasets/mit-ocw/evaluation-profiles.json").toFile())
                        .get("cases")) {
            count++;
            Set<String> done = new HashSet<>(), relevant = new HashSet<>();
            for (JsonNode x : p.get("completed")) done.add(x.asText());
            for (JsonNode x : p.get("relevant")) relevant.add(x.asText());
            String query = p.get("query").asText();
            List<Recommendation> results = ranker.rank(catalog, done, query, 3);
            List<Course> baseline =
                    catalog.stream()
                            .filter(c -> ranker.eligible(c, done))
                            .filter(c -> overlap(c, query) > 0)
                            .sorted(
                                    Comparator.<Course>comparingInt(c -> overlap(c, query))
                                            .reversed()
                                            .thenComparing(Course::getCode))
                            .limit(3)
                            .collect(Collectors.toList());
            for (Recommendation r : results)
                assertTrue(ranker.eligible(r.getCourse(), done), p.get("id").asText());
            long n =
                    results.stream()
                            .filter(r -> relevant.contains(r.getCourse().getCode()))
                            .count();
            long b = baseline.stream().filter(c -> relevant.contains(c.getCode())).count();
            if (n > 0) hits++;
            if (b > 0) baseHits++;
            precision += n / 3.0;
            basePrecision += b / 3.0;
            rows.add(
                    p.get("id").asText()
                            + ","
                            + results.stream()
                                    .map(r -> r.getCourse().getCode())
                                    .collect(Collectors.joining("|"))
                            + ","
                            + baseline.stream()
                                    .map(Course::getCode)
                                    .collect(Collectors.joining("|"))
                            + ","
                            + (n > 0 ? 1 : 0)
                            + ","
                            + (b > 0 ? 1 : 0)
                            + ","
                            + n / 3.0
                            + ","
                            + b / 3.0
                            + ",0");
        }
        assertEquals(20, count);
        if ("true".equals(System.getenv("M2_EVALUATION"))) {
            Path out = Paths.get("../docs/evaluation/catalog-v2");
            Files.createDirectories(out);
            Files.write(out.resolve("results.csv"), rows, StandardCharsets.UTF_8);
            Map<String, Object> metrics = new LinkedHashMap<>();
            metrics.put("syntheticCases", count);
            metrics.put("tfidfHitAt3", hits / (double) count);
            metrics.put("keywordHitAt3", baseHits / (double) count);
            metrics.put("tfidfPrecisionAt3", precision / count);
            metrics.put("keywordPrecisionAt3", basePrecision / count);
            metrics.put("eligibilityViolations", 0);
            metrics.put(
                    "limitation",
                    "Author-labelled synthetic cases, no independent human review. Keyword baseline"
                        + " uses identical eligibility rules. Precision denominator fixed at 3.");
            json.writerWithDefaultPrettyPrinter()
                    .writeValue(out.resolve("metrics.json").toFile(), metrics);
        }
    }

    private int overlap(Course c, String query) {
        Set<String> tokens =
                Recommender.terms(c.getTitle() + " " + c.getDescription() + " " + c.getTopics())
                        .keySet();
        return (int) Recommender.terms(query).keySet().stream().filter(tokens::contains).count();
    }
}
