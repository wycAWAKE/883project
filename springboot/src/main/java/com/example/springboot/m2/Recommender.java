package com.example.springboot.m2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * M2 可离线运行的确定性推荐器：资格检查 + TF-IDF 向量 + 余弦相似度排序。
 *
 * <p>原算法直接使用它；DeepSeek 模式只复用资格检查，异常时才使用 TF-IDF 回退。没有模型训练、网络请求或数据库写入，便于单独测试。公式、示例和边界见 CODE_GUIDE.md#recommender。
 */
public class Recommender {
    private static final Set<String> STOP_WORDS =
            new HashSet<>(
                    Arrays.asList(
                            "a", "an", "the", "and", "or", "of", "to", "in", "for", "with", "on",
                            "is", "are", "this", "that", "be", "as", "by", "from", "it", "will",
                            "we", "you", "your", "my", "i", "want", "learn", "course", "courses"));

    /** 对英文文本分词、去掉常见停用词，并记录每个词出现的次数。 */
    public static Map<String, Double> terms(String text) {
        Map<String, Double> counts = new TreeMap<>();
        for (String term : text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (term.length() > 1 && !STOP_WORDS.contains(term)) {
                counts.put(term, counts.getOrDefault(term, 0.0) + 1);
            }
        }
        return counts;
    }

    /** 先检查是否有资格推荐：不同先修组必须全部满足，组内任一课程即可。 */
    public boolean eligible(Course course, Set<String> completed) {
        if (!course.isActive() || completed.contains(course.getCode())) {
            return false;
        }
        if ("no_formal_course".equals(course.getPrerequisiteStatus())) {
            return course.getPrerequisiteGroups().isEmpty();
        }
        if (!"structured".equals(course.getPrerequisiteStatus())
                || course.getPrerequisiteGroups().isEmpty()) {
            return false;
        }
        for (List<String> group : course.getPrerequisiteGroups()) {
            if (group.isEmpty() || Collections.disjoint(group, completed)) {
                return false;
            }
        }
        return true;
    }

    /** 面向学生的原算法入口，最多返回三门；不足三门时不会补入无资格或零匹配课程。 */
    public List<Recommendation> rank(
            List<Course> courses, Set<String> completed, String query, int topK) {
        if (topK < 1 || topK > 3) {
            throw new IllegalArgumentException("topK must be between 1 and 3");
        }
        return rankCandidates(courses, completed, query, topK);
    }

    /** 原算法的内部排序入口；最多三十门，分数仍是文本匹配分数。 */
    public List<Recommendation> rankCandidates(
            List<Course> courses, Set<String> completed, String query, int topK) {
        if (topK < 1 || topK > 30) {
            throw new IllegalArgumentException("candidate limit must be between 1 and 30");
        }
        // IDF 始终来自完整输入目录；不能先按学生资格过滤，否则同一查询的权重会改变。
        List<Map<String, Double>> courseTermCounts = tokenizeCourses(courses);
        Map<String, Double> inverseDocumentFrequency = calculateIdf(courseTermCounts);
        Map<String, Double> queryVector = normalizedVector(terms(query), inverseDocumentFrequency);
        List<Recommendation> recommendations = new ArrayList<>();
        if (queryVector.isEmpty()) {
            return recommendations;
        }
        for (int index = 0; index < courses.size(); index++) {
            Course course = courses.get(index);
            if (!eligible(course, completed)) {
                continue;
            }
            Map<String, Double> courseVector =
                    normalizedVector(courseTermCounts.get(index), inverseDocumentFrequency);
            double score = cosineSimilarity(queryVector, courseVector);
            if (score > 0) {
                recommendations.add(explainMatch(course, score, queryVector, courseVector));
            }
        }
        recommendations.sort(
                Comparator.comparingDouble(Recommendation::getScore)
                        .reversed()
                        .thenComparing(recommendation -> recommendation.getCourse().getCode()));
        return recommendations.stream().limit(topK).collect(Collectors.toList());
    }

    /** 保持与输入课程相同的顺序，后续可按下标取出对应词频。 */
    private List<Map<String, Double>> tokenizeCourses(List<Course> courses) {
        List<Map<String, Double>> courseTermCounts = new ArrayList<>();
        for (Course course : courses) {
            String searchableText = course.getTitle() + " " + course.getDescription() + " " + course.getTopics();
            courseTermCounts.add(terms(searchableText));
        }
        return courseTermCounts;
    }

    /** 每个词在一门课程中只计一次；在越多课程中出现，区分度越低。 */
    private Map<String, Double> calculateIdf(List<Map<String, Double>> courseTermCounts) {
        Map<String, Integer> documentFrequency = new HashMap<>();
        for (Map<String, Double> courseTerms : courseTermCounts) {
            for (String term : courseTerms.keySet()) {
                documentFrequency.put(term, documentFrequency.getOrDefault(term, 0) + 1);
            }
        }
        Map<String, Double> idf = new HashMap<>();
        for (String term : documentFrequency.keySet()) {
            idf.put(
                    term,
                    Math.log((1.0 + courseTermCounts.size()) / (1.0 + documentFrequency.get(term))) + 1);
        }
        return idf;
    }

    /** 两个向量已归一化，因此共同词权重的点积就是余弦相似度。 */
    private double cosineSimilarity(Map<String, Double> queryVector, Map<String, Double> courseVector) {
        double score = 0;
        for (String term : queryVector.keySet()) {
            if (courseVector.containsKey(term)) {
                score += queryVector.get(term) * courseVector.get(term);
            }
        }
        return score;
    }

    private Recommendation explainMatch(
            Course course, double score, Map<String, Double> queryVector, Map<String, Double> courseVector) {
        List<String> matchingTerms = new ArrayList<>();
        for (String term : queryVector.keySet()) {
            if (courseVector.containsKey(term)) {
                matchingTerms.add(term);
            }
        }
        String explanation = "Matches your interests/goals: " + String.join(", ", matchingTerms)
                + ". Completed courses excluded; published prerequisite course"
                + " rules satisfied. Historical OCW learning guidance only.";
        return new Recommendation(course, score, matchingTerms, explanation);
    }

    /** 使用 log-TF × 平滑 IDF 生成稀疏向量，再除以 L2 长度以便比较余弦相似度。 */
    private Map<String, Double> normalizedVector(Map<String, Double> counts, Map<String, Double> idf) {
        Map<String, Double> weights = new TreeMap<>();
        double squaredLength = 0;
        for (String term : counts.keySet()) {
            if (idf.containsKey(term)) {
                double weight = (1 + Math.log(counts.get(term))) * idf.get(term);
                weights.put(term, weight);
                squaredLength += weight * weight;
            }
        }
        double vectorLength = Math.sqrt(squaredLength);
        if (vectorLength > 0) {
            weights.replaceAll((term, weight) -> weight / vectorLength);
        }
        return weights;
    }
}
