package com.example.springboot.m2;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class DeepSeekRecommendationTest {
    private final ObjectMapper json = new ObjectMapper();

    private Course course(String code, String status) {
        Course c = new Course();
        c.setCode(code);
        c.setTitle("Python programming");
        c.setDescription("Learn python programming with practical examples");
        c.setTopics("computer science");
        c.setActive(true);
        c.setPrerequisiteStatus(status);
        return c;
    }

    private DeepSeekClient client(RestTemplate http) {
        return new DeepSeekClient(
                new MockEnvironment()
                        .withProperty("M2_AI_ENABLED", "true")
                        .withProperty("DEEPSEEK_API_KEY", "test-only"),
                json,
                http);
    }

    private String envelope(String content, String reason) throws Exception {
        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put("finish_reason", reason);
        choice.put("message", Collections.singletonMap("content", content));
        return json.writeValueAsString(
                Collections.singletonMap("choices", Collections.singletonList(choice)));
    }

    @Test
    void disabledProviderFallsBackWithoutNetworkAndBaselineStillWorks() {
        DeepSeekClient client = new DeepSeekClient(new MockEnvironment(), json, new RestTemplate());
        RecommendationOutcome result =
                new AiCourseRecommender(client, json)
                        .recommend(
                                Arrays.asList(course("A", "no_formal_course")),
                                Collections.emptySet(),
                                "python",
                                3);
        assertEquals("FALLBACK", result.getMode());
        assertEquals("NOT_CONFIGURED", result.getReason());
        assertEquals(1, result.getResults().size());
        assertNotNull(result.getResults().get(0).getScore());
    }

    @Test
    void validChineseQuerySelectsOnlyEligibleCatalogCourses() throws Exception {
        RestTemplate http = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(http);
        DeepSeekClient client = client(http);
        server.expect(requestTo(DeepSeekClient.ENDPOINT))
                .andExpect(header("Authorization", "Bearer test-only"))
                .andExpect(content().string(containsString("人工智能")))
                .andExpect(content().string(not(containsString("SECRET-INELIGIBLE"))))
                .andRespond(
                        withSuccess(
                                envelope(
                                        "{\"recommendations\":[{\"code\":\"A\",\"explanation\":\"A"
                                            + " practical starting point for programming.\"}]}",
                                        "stop"),
                                MediaType.APPLICATION_JSON));
        RecommendationOutcome result =
                new AiCourseRecommender(client, json)
                        .recommend(
                                Arrays.asList(
                                        course("A", "no_formal_course"),
                                        course("SECRET-INELIGIBLE", "review_required")),
                                Collections.emptySet(),
                                "我想学习人工智能",
                                3);
        assertEquals("LLM_ASSISTED", result.getMode());
        assertEquals("A", result.getResults().get(0).getCourse().getCode());
        assertNull(result.getResults().get(0).getScore());
        server.verify();
    }

    @Test
    void invalidSelectionsAndTruncationFailClosed() throws Exception {
        for (String selection :
                Arrays.asList(
                        "{\"recommendations\":[{\"code\":\"INVENTED\",\"explanation\":\"Made up"
                            + " course\"}]}",
                        "{\"recommendations\":[{\"code\":\"A\",\"explanation\":\"Reason"
                            + " one\"},{\"code\":\"A\",\"explanation\":\"Reason two\"}]}",
                        "not json")) {
            RestTemplate http = new RestTemplate();
            MockRestServiceServer server = MockRestServiceServer.createServer(http);
            DeepSeekClient client = client(http);
            server.expect(anything())
                    .andRespond(
                            withSuccess(envelope(selection, "stop"), MediaType.APPLICATION_JSON));
            RecommendationOutcome result =
                    new AiCourseRecommender(client, json)
                            .recommend(
                                    Arrays.asList(course("A", "no_formal_course")),
                                    Collections.emptySet(),
                                    "python",
                                    3);
            assertEquals("FALLBACK", result.getMode());
            assertEquals("A", result.getResults().get(0).getCourse().getCode());
            server.verify();
        }
        RestTemplate http = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(http);
        DeepSeekClient client = client(http);
        server.expect(anything())
                .andRespond(withSuccess(envelope("{}", "length"), MediaType.APPLICATION_JSON));
        assertEquals(
                "FALLBACK",
                new AiCourseRecommender(client, json)
                        .recommend(
                                Arrays.asList(course("A", "no_formal_course")),
                                Collections.emptySet(),
                                "python",
                                3)
                        .getMode());
        server.verify();
    }

    @Test
    void upstreamAuthFailureDoesNotBecomeStudentAuthFailure() {
        RestTemplate http = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(http);
        DeepSeekClient client = client(http);
        server.expect(anything()).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        RecommendationOutcome result =
                new AiCourseRecommender(client, json)
                        .recommend(
                                Arrays.asList(course("A", "no_formal_course")),
                                Collections.emptySet(),
                                "python",
                                3);
        assertEquals("FALLBACK", result.getMode());
        assertFalse(result.getResults().isEmpty());
        server.verify();
    }

    @Test
    void limitAndModelEmptyResultDoNotInventResults() throws Exception {
        DeepSeekClient limited =
                new DeepSeekClient(
                        new MockEnvironment()
                                .withProperty("M2_AI_ENABLED", "true")
                                .withProperty("DEEPSEEK_API_KEY", "test-only")
                                .withProperty("M2_AI_DAILY_REQUEST_LIMIT", "0"),
                        json,
                        new RestTemplate());
        assertEquals(
                "LIMIT_REACHED",
                new AiCourseRecommender(limited, json)
                        .recommend(
                                Arrays.asList(course("A", "no_formal_course")),
                                Collections.emptySet(),
                                "python",
                                3)
                        .getReason());
        RestTemplate http = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(http);
        DeepSeekClient client = client(http);
        server.expect(anything())
                .andExpect(content().string(containsString("I do not want Python")))
                .andRespond(
                        withSuccess(
                                envelope("{\"recommendations\":[]}", "stop"),
                                MediaType.APPLICATION_JSON));
        RecommendationOutcome result =
                new AiCourseRecommender(client, json)
                        .recommend(
                                Arrays.asList(course("A", "no_formal_course")),
                                Collections.emptySet(),
                                "I do not want Python",
                                3);
        assertEquals("LLM_ASSISTED", result.getMode());
        assertTrue(result.getResults().isEmpty());
        server.verify();
    }

    @Test
    void providerTimeoutAndExcessResultsFallBack() throws Exception {
        RestTemplate http = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(http);
        DeepSeekClient client = client(http);
        server.expect(anything())
                .andRespond(
                        request -> {
                            throw new java.net.SocketTimeoutException("test timeout");
                        });
        assertEquals(
                "FALLBACK",
                new AiCourseRecommender(client, json)
                        .recommend(
                                Arrays.asList(course("A", "no_formal_course")),
                                Collections.emptySet(),
                                "python",
                                3)
                        .getMode());
        server.reset();
        server.expect(anything())
                .andRespond(
                        withSuccess(
                                envelope(
                                        "{\"recommendations\":[{\"code\":\"A\",\"explanation\":\"One\"},{\"code\":\"B\",\"explanation\":\"Two\"}]}",
                                        "stop"),
                                MediaType.APPLICATION_JSON));
        assertEquals(
                "FALLBACK",
                new AiCourseRecommender(client, json)
                        .recommend(
                                Arrays.asList(
                                        course("A", "no_formal_course"),
                                        course("B", "no_formal_course")),
                                Collections.emptySet(),
                                "python",
                                1)
                        .getMode());
        server.verify();
    }

    @Test
    void rejectsTrailingJsonAndDuplicateFields() throws Exception {
        for (String content :
                Arrays.asList(
                        "{\"recommendations\":[]} {}",
                        "{\"recommendations\":[],\"recommendations\":[]}")) {
            RestTemplate http = new RestTemplate();
            MockRestServiceServer server = MockRestServiceServer.createServer(http);
            DeepSeekClient client = new DeepSeekClient(new MockEnvironment(), json, http);
            server.expect(anything())
                    .andRespond(withSuccess(envelope(content, "stop"), MediaType.APPLICATION_JSON));
            assertThrows(Exception.class, () -> client.complete("test", Collections.emptyMap()));
            server.verify();
        }
    }
}
