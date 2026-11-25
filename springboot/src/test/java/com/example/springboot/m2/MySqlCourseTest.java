package com.example.springboot.m2;

import com.example.springboot.m2.mapper.CourseMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;

@SpringBootTest
@ActiveProfiles("m2local")
@EnabledIfEnvironmentVariable(named = "M2_MYSQL_TEST", matches = "true")
class MySqlCourseTest {
    @Autowired CourseService service;
    @Autowired JdbcTemplate jdbc;
    @SpyBean CourseMapper mapper;
    @SpyBean DeepSeekClient deepSeekClient;

    @BeforeEach
    void setup() {
        for (String u : Arrays.asList("m2_test_a", "m2_test_b"))
            jdbc.update(
                    "INSERT INTO student(username,password,name) VALUES(?,?,?)",
                    u,
                    "not-a-login-account",
                    u);
        jdbc.update(
                "INSERT INTO m2_completed(student_username,course_code,provenance)"
                    + " VALUES('m2_test_b','6.0001','test_fixture'),"
                    + "('m2_test_b','11.125','test_fixture')");
    }

    @AfterEach
    void cleanup() {
        reset(mapper, deepSeekClient);
        for (String table : Arrays.asList("m2_history", "m2_profile", "m2_completed"))
            jdbc.update(
                    "DELETE FROM "
                            + table
                            + " WHERE student_username IN ('m2_test_a','m2_test_b')");
        jdbc.update("DELETE FROM student WHERE username IN ('m2_test_a','m2_test_b')");
    }

    @Test
    void realCatalogPersonalizationIsolationAndDurableHistory() throws Exception {
        assertTrue(service.catalog().size() >= 300);
        Course algorithms = service.catalog().stream()
                .filter(c -> c.getCode().equals("6.006"))
                .findFirst().orElseThrow(AssertionError::new);
        assertEquals("review_required", algorithms.getPrerequisiteStatus());
        assertFalse(new Recommender().eligible(
                algorithms, new HashSet<>(Arrays.asList("6.0001", "6.042J"))));
        List<Recommendation> a = service.recommend("m2_test_a", "education", "teaching", 3);
        assertTrue(a.stream().anyMatch(r -> r.getCourse().getCode().equals("11.125")));
        List<Recommendation> b = service.recommend("m2_test_b", "education", "teaching", 3);
        assertTrue(b.stream().noneMatch(r -> r.getCourse().getCode().equals("11.125")));
        assertTrue(a.stream().noneMatch(r -> r.getCourse().getCode().equals("6.0001") || r.getCourse().getCode().equals("6.189")));
        assertEquals(1, service.history("m2_test_a").size());
        assertEquals(1, service.history("m2_test_b").size());
        assertEquals(
                "education", ((Map<?, ?>) service.me("m2_test_a").get("profile")).get("interests"));
        assertTrue(service.history("m2_test_a").get(0).containsKey("results"));
    }

    @Test
    void failedHistoryInsertRollsBackProfileChange() throws Exception {
        service.recommend("m2_test_a", "python", "old", 3);
        doThrow(new IllegalStateException("Injected persistence failure"))
                .when(mapper)
                .addHistory(eq("m2_test_a"), anyString(), anyString());
        assertThrows(
                IllegalStateException.class,
                () -> service.recommend("m2_test_a", "algorithms", "changed", 3));
        assertEquals(
                "python", ((Map<?, ?>) service.me("m2_test_a").get("profile")).get("interests"));
        assertEquals(1, service.history("m2_test_a").size());
    }

    @Test
    void assistedFallbackIsPersistedOnceAndLegacyHistoryRemainsReadable() throws Exception {
        RecommendationOutcome outcome = service.assisted("m2_test_a", "python", "programming", 3);
        assertEquals("FALLBACK", outcome.getMode());
        List<Map<String, Object>> history = service.history("m2_test_a");
        assertEquals(1, history.size());
        assertEquals("FALLBACK", history.get(0).get("mode"));
        assertTrue(
                ((com.fasterxml.jackson.databind.JsonNode) history.get(0).get("results"))
                        .isArray());
        service.recommend("m2_test_a", "python", "", 3);
        assertEquals("BASELINE", service.history("m2_test_a").get(0).get("mode"));
        doThrow(new IllegalStateException("Injected persistence failure"))
                .when(mapper)
                .addHistory(eq("m2_test_a"), anyString(), anyString());
        assertThrows(
                IllegalStateException.class,
                () -> service.assisted("m2_test_a", "new interest", "", 3));
        assertEquals(
                "python", ((Map<?, ?>) service.me("m2_test_a").get("profile")).get("interests"));
        assertEquals(2, service.history("m2_test_a").size());
    }

    @Test
    void modelSelectedCourseWithNoTfidfScoreIsPersistedInRealMysql() throws Exception {
        when(deepSeekClient.configured()).thenReturn(true);
        when(deepSeekClient.acquire()).thenReturn(true);
        com.fasterxml.jackson.databind.ObjectMapper json = new com.fasterxml.jackson.databind.ObjectMapper();
        doReturn(json.readTree("{\"recommendations\":[{\"code\":\"11.125\",\"explanation\":\"The course explores teaching and education.\"}]}"))
                .when(deepSeekClient).complete(anyString(), any());
        doNothing().when(deepSeekClient).release();
        RecommendationOutcome outcome = service.assisted("m2_test_a", "人工智能", "", 3);
        assertEquals("LLM_ASSISTED", outcome.getMode());
        assertEquals("11.125", outcome.getResults().get(0).getCourse().getCode());
        assertNull(outcome.getResults().get(0).getScore());
        List<Map<String, Object>> history = service.history("m2_test_a");
        assertEquals(1, history.size());
        assertEquals("LLM_ASSISTED", history.get(0).get("mode"));
        com.fasterxml.jackson.databind.JsonNode saved = (com.fasterxml.jackson.databind.JsonNode) history.get(0).get("results");
        assertTrue(saved.get(0).get("score").isNull());
    }
}
