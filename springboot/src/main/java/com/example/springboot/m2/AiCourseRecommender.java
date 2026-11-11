package com.example.springboot.m2;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * M2 大模型推荐编排：Java 判断课程资格，DeepSeek 直接从全部合格课程中选择并解释。
 *
 * <p>模型成功路径不使用 TF-IDF 缩小候选；无配置、限额或供应商异常时才运行原算法回退。 本类不保存数据库记录，也不负责 HTTP 登录。详细流程见
 * CODE_GUIDE.md#aicourserecommender。
 */
@Component
public class AiCourseRecommender {
    private static final int MAX_PAYLOAD_CHARS = 1_000_000;
    private static final int MAX_DESCRIPTION_CHARS = 900;
    private final DeepSeekClient client;
    private final ObjectMapper json;

    public AiCourseRecommender(DeepSeekClient client, ObjectMapper json) {
        this.client = client;
        this.json = json;
    }

    /** 所有课程先通过服务器端资格检查；实际使用模式随结果一起返回。 */
    public RecommendationOutcome recommend(
            List<Course> catalog, Set<String> completed, String query, int topK) {
        if (topK < 1 || topK > 3) {
            throw new IllegalArgumentException("topK must be between 1 and 3");
        }
        Recommender rules = new Recommender();
        List<Course> eligibleCourses = findEligibleCourses(catalog, completed, rules);
        if (eligibleCourses.isEmpty()) {
            // 没有合格课程时无需付费调用模型，也不能声称模型已被调用。
            return new RecommendationOutcome(
                    Collections.emptyList(),
                    "ELIGIBILITY_FILTER",
                    "NO_ELIGIBLE_COURSES",
                    "No courses currently meet the recorded prerequisite rules.",
                    null);
        }
        if (!client.configured()) {
            return fallback(rules, catalog, completed, query, topK, "NOT_CONFIGURED");
        }

        // 每门合格课都进入白名单；只发送公开课程摘要和本次兴趣，不发送用户身份或成绩。
        Map<String, Course> byCode = new LinkedHashMap<>();
        for (Course course : eligibleCourses) {
            byCode.put(course.getCode(), course);
        }
        Map<String, Object> payload = buildPayload(eligibleCourses, query, topK);
        try {
            // 数据量过大时明确回退，不能悄悄截掉后面的合格课程。
            if (json.writeValueAsString(payload).length() > MAX_PAYLOAD_CHARS) {
                return fallback(rules, catalog, completed, query, topK, "CATALOG_TOO_LARGE");
            }
        } catch (Exception ignored) {
            return fallback(
                    rules, catalog, completed, query, topK, "PROVIDER_UNAVAILABLE_OR_INVALID");
        }
        if (!client.acquire()) {
            return fallback(rules, catalog, completed, query, topK, "LIMIT_REACHED");
        }
        try {
            // 模型直接理解原始兴趣与否定，且只能选择本次合格课程编号。
            JsonNode selected =
                    client.complete(
                                    "Choose up to maxResults courses ONLY from candidates based on"
                                        + " the student's original query, including Chinese or"
                                        + " English, interests and exclusions. Use semantic"
                                        + " relevance; return an empty array if no course is"
                                        + " suitable. Return concise English explanations based"
                                        + " only on supplied course details. Do not assert credits,"
                                        + " availability, guaranteed outcomes or qualifications."
                                        + " json example:"
                                        + " {\"recommendations\":[{\"code\":\"6.0001\",\"explanation\":\"Introduces"
                                        + " Python for your stated interest.\"}]}",
                                    payload)
                            .path("recommendations");
            List<Recommendation> results =
                    validateRecommendations(selected, topK, byCode, completed, rules);
            return new RecommendationOutcome(
                    results,
                    "LLM_ASSISTED",
                    "OK",
                    "DeepSeek selected these courses from eligible courses. AI explanations should"
                        + " be checked against the source.",
                    client.model());
        } catch (Exception ignored) {
            return fallback(
                    rules, catalog, completed, query, topK, "PROVIDER_UNAVAILABLE_OR_INVALID");
        } finally {
            client.release();
        }
    }

    /** 资格检查独立于模型；全部合格课程都会发送，不按 TF-IDF 截取。 */
    private List<Course> findEligibleCourses(
            List<Course> catalog, Set<String> completed, Recommender rules) {
        List<Course> eligibleCourses = new ArrayList<>();
        for (Course course : catalog) {
            if (rules.eligible(course, completed)) {
                eligibleCourses.add(course);
            }
        }
        return eligibleCourses;
    }

    /** 只构造公开课程摘要和查询，不添加账号、成绩或历史记录。 */
    private Map<String, Object> buildPayload(List<Course> eligibleCourses, String query, int topK) {
        List<Map<String, String>> summaries = new ArrayList<>();
        for (Course course : eligibleCourses) {
            Map<String, String> summary = new LinkedHashMap<>();
            summary.put("code", course.getCode());
            summary.put("title", course.getTitle());
            summary.put("topics", course.getTopics());
            String description = course.getDescription();
            int summaryLength = Math.min(MAX_DESCRIPTION_CHARS, description.length());
            summary.put("description", description.substring(0, summaryLength));
            summaries.add(summary);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("query", query);
        payload.put("maxResults", topK);
        payload.put("candidates", summaries);
        return payload;
    }

    /** 模型输出是不可信输入：逐项核对数量、白名单、重复和先修资格。 */
    private List<Recommendation> validateRecommendations(
            JsonNode selected, int topK, Map<String, Course> byCode,
            Set<String> completed, Recommender rules) {
        if (!selected.isArray() || selected.size() > topK) {
            throw new IllegalStateException();
        }
        List<Recommendation> results = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonNode item : selected) {
            String code = requiredText(item, "code", 32);
            String explanation = requiredText(item, "explanation", 1200);
            Course course = byCode.get(code);
            if (course == null || !seen.add(code) || !rules.eligible(course, completed)) {
                throw new IllegalStateException();
            }
            // 模型没有给出 TF-IDF 分数；null 比虚构 0 分更准确。
            results.add(new Recommendation(course, null, Collections.emptyList(), explanation));
        }
        return results;
    }

    /** 验证原始长度后再去除首尾空格，保持与现有输出合同一致。 */
    private String requiredText(JsonNode node, String key, int maxLength) {
        JsonNode value = node.path(key);
        if (!value.isTextual() || value.asText().trim().isEmpty() || value.asText().length() > maxLength) {
            throw new IllegalStateException();
        }
        return value.asText().trim();
    }

    /** 仅在无法使用 DeepSeek 时计算 TF-IDF，且在返回中明确标记回退。 */
    private RecommendationOutcome fallback(
            Recommender rules,
            List<Course> catalog,
            Set<String> completed,
            String query,
            int topK,
            String reason) {
        List<Recommendation> results = rules.rank(catalog, completed, query, topK);
        return new RecommendationOutcome(
                results,
                "FALLBACK",
                reason,
                "DeepSeek is unavailable. Results use the original offline algorithm; English"
                    + " keywords work best.",
                null);
    }
}
