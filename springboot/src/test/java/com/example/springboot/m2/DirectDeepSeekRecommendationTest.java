package com.example.springboot.m2;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class DirectDeepSeekRecommendationTest {
    private final ObjectMapper json = new ObjectMapper();

    private Course course(String code, String status) {
        Course c = new Course();
        c.setCode(code);
        c.setTitle("Neural networks");
        c.setDescription("Foundations of neural networks and backpropagation.");
        c.setTopics("neural networks");
        c.setPrerequisiteStatus(status);
        c.setActive(true);
        return c;
    }

    private String envelope(String content) throws Exception {
        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put("finish_reason", "stop");
        choice.put("message", Collections.singletonMap("content", content));
        return json.writeValueAsString(
                Collections.singletonMap("choices", Collections.singletonList(choice)));
    }

    @Test
    void modelSeesEveryEligibleCourseIncludingBeyondThirtyAndNoKeywordMatch() throws Exception {
        RestTemplate http = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(http);
        DeepSeekClient client =
                new DeepSeekClient(
                        new MockEnvironment()
                                .withProperty("M2_AI_ENABLED", "true")
                                .withProperty("DEEPSEEK_API_KEY", "test-only"),
                        json,
                        http);
        List<Course> catalog = new ArrayList<>();
        for (int i = 1; i <= 40; i++)
            catalog.add(course(String.format("C%02d", i), "no_formal_course"));
        catalog.add(course("REVIEW-ONLY", "review_required"));
        catalog.add(course("ALREADY-DONE", "no_formal_course"));
        server.expect(requestTo(DeepSeekClient.ENDPOINT))
                .andExpect(content().string(containsString("C40")))
                .andExpect(content().string(containsString("人工智能")))
                .andExpect(content().string(not(containsString("REVIEW-ONLY"))))
                .andExpect(content().string(not(containsString("ALREADY-DONE"))))
                .andRespond(
                        withSuccess(
                                envelope(
                                        "{\"recommendations\":[{\"code\":\"C40\",\"explanation\":\"Neural"
                                            + " networks are related to your AI interest.\"}]}"),
                                MediaType.APPLICATION_JSON));
        RecommendationOutcome result =
                new AiCourseRecommender(client, json)
                        .recommend(catalog, Collections.singleton("ALREADY-DONE"), "我想学习人工智能", 3);
        assertEquals("LLM_ASSISTED", result.getMode());
        assertEquals("C40", result.getResults().get(0).getCourse().getCode());
        assertNull(result.getResults().get(0).getScore(), "AI does not produce a TF-IDF score");
        assertTrue(result.getResults().get(0).getMatchingTerms().isEmpty());
        server.verify();
    }

    @Test
    void noEligibleCoursesDoesNotPretendModelWasCalled() {
        RestTemplate http = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(http);
        DeepSeekClient client =
                new DeepSeekClient(
                        new MockEnvironment()
                                .withProperty("M2_AI_ENABLED", "true")
                                .withProperty("DEEPSEEK_API_KEY", "test-only"),
                        json,
                        http);
        RecommendationOutcome result =
                new AiCourseRecommender(client, json)
                        .recommend(
                                Collections.singletonList(course("R", "review_required")),
                                Collections.emptySet(),
                                "python",
                                3);
        assertEquals("ELIGIBILITY_FILTER", result.getMode());
        assertEquals("NO_ELIGIBLE_COURSES", result.getReason());
        assertTrue(result.getResults().isEmpty());
        assertNull(result.getModel());
        server.verify();
    }

    @Test
    void oversizedCatalogFallsBackWithoutSilentlyDroppingEligibleCourses() {
        RestTemplate http = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(http);
        DeepSeekClient client = new DeepSeekClient(new MockEnvironment().withProperty("M2_AI_ENABLED", "true")
                .withProperty("DEEPSEEK_API_KEY", "test-only"), json, http);
        List<Course> catalog = new ArrayList<>();
        String description = new String(new char[900]).replace('\0', 'x');
        for (int i = 0; i < 1200; i++) {
            Course c = course("C" + i, "no_formal_course");
            c.setDescription(description);
            catalog.add(c);
        }
        RecommendationOutcome result = new AiCourseRecommender(client, json)
                .recommend(catalog, Collections.emptySet(), "neural networks", 3);
        assertEquals("FALLBACK", result.getMode());
        assertEquals("CATALOG_TOO_LARGE", result.getReason());
        assertFalse(result.getResults().isEmpty());
        server.verify();
    }
}
