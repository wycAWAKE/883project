package com.example.springboot.m2;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RecommenderTest {
    private Course course(String code, String words, String status, String[]... groups) {
        Course c = new Course();
        c.setCode(code);
        c.setTitle(words);
        c.setDescription(words);
        c.setTopics(words);
        c.setActive(true);
        c.setPrerequisiteStatus(status);
        List<List<String>> list = new ArrayList<>();
        for (String[] group : groups) list.add(Arrays.asList(group));
        c.setPrerequisiteGroups(list);
        return c;
    }

    private Set<String> done(String... codes) {
        return new HashSet<>(Arrays.asList(codes));
    }

    @Test
    void excludesCompletedAndUnknownRequirements() {
        Course a = course("A", "python", "no_formal_course"),
                b = course("B", "python", "review_required");
        assertTrue(new Recommender().rank(Arrays.asList(a, b), done("A"), "python", 3).isEmpty());
    }

    @Test
    void allGroupsRequiredButAlternativesAllowedIncludingExternalCourses() {
        Course c =
                course(
                        "C",
                        "data algorithms",
                        "structured",
                        new String[] {"A", "B"},
                        new String[] {"EXTERNAL"});
        Recommender r = new Recommender();
        assertTrue(r.rank(Arrays.asList(c), done("A"), "algorithms", 3).isEmpty());
        assertEquals(1, r.rank(Arrays.asList(c), done("B", "EXTERNAL"), "algorithms", 3).size());
    }

    @Test
    void malformedStructuredRequirementsFailClosed() {
        Course c = course("C", "python", "structured");
        assertTrue(new Recommender().rank(Arrays.asList(c), done(), "python", 3).isEmpty());
    }

    @Test
    void stableTiesAndFaithfulExplanation() {
        Course a = course("A", "python programming", "no_formal_course"),
                b = course("B", "python programming", "no_formal_course");
        List<Recommendation> result =
                new Recommender().rank(Arrays.asList(b, a), done(), "python", 1);
        assertEquals("A", result.get(0).getCourse().getCode());
        assertEquals(Arrays.asList("python"), result.get(0).getMatchingTerms());
        assertTrue(result.get(0).getScore() > 0 && result.get(0).getScore() <= 1.000001);
        assertTrue(result.get(0).getExplanation().contains("python"));
    }

    @Test
    void noMatchOrEmptyQueryReturnsNoInventedRecommendations() {
        List<Course> catalog = Arrays.asList(course("A", "python", "no_formal_course"));
        assertTrue(new Recommender().rank(catalog, done(), "unicornzz", 3).isEmpty());
        assertTrue(new Recommender().rank(catalog, done(), "the and", 3).isEmpty());
    }

    @Test
    void boundsAndInactiveCourses() {
        Recommender r = new Recommender();
        Course c = course("A", "python", "no_formal_course");
        c.setActive(false);
        assertTrue(r.rank(Arrays.asList(c), done(), "python", 3).isEmpty());
        assertThrows(
                IllegalArgumentException.class,
                () -> r.rank(Arrays.asList(c), done(), "python", 4));
        assertThrows(
                IllegalArgumentException.class,
                () -> r.rank(Arrays.asList(c), done(), "python", 0));
    }
}
