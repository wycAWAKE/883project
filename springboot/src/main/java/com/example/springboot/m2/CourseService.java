package com.example.springboot.m2;

import com.example.springboot.m2.mapper.CourseMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import java.util.*;

/**
 * M2 业务编排层：组合数据库资料、选择推荐方式、保存结果并兼容旧历史。
 *
 * <p>Controller 只处理 HTTP，本类负责把 Mapper、两个推荐器和事务保存组件连起来。 这里不把可能等待网络的整个推荐过程包进数据库事务。说明见
 * CODE_GUIDE.md#courseservice。
 */
@Service
public class CourseService {
    private final CourseMapper mapper;
    private final ObjectMapper json;
    private final AiCourseRecommender ai;
    private final CourseHistoryWriter historyWriter;

    public CourseService(
            CourseMapper mapper,
            ObjectMapper json,
            AiCourseRecommender ai,
            CourseHistoryWriter historyWriter) {
        this.mapper = mapper;
        this.json = json;
        this.ai = ai;
        this.historyWriter = historyWriter;
    }

    /** 把 Mapper 返回的课程基本资料与扁平的先修表组装成 List<Course>。 */
    public List<Course> catalog() {
        List<Course> courses = mapper.catalog();
        Map<String, SortedMap<Integer, List<String>>> groups = new HashMap<>();
        // 数据库按课程和组号存储先修条件；同组是 OR，不同组是 AND。
        for (Map<String, Object> row : mapper.prerequisites()) {
            String code = (String) row.get("courseCode");
            int group = ((Number) row.get("groupNo")).intValue();
            groups.computeIfAbsent(code, k -> new TreeMap<>())
                    .computeIfAbsent(group, k -> new ArrayList<>())
                    .add((String) row.get("prerequisiteCode"));
        }
        for (Course c : courses)
            if (groups.containsKey(c.getCode()))
                c.setPrerequisiteGroups(new ArrayList<>(groups.get(c.getCode()).values()));
        return courses;
    }

    /** 读取当前学生的受控已修记录和兴趣画像；此方法不允许自行写入成绩。 */
    public Map<String, Object> me(String username) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("studentUsername", username);
        result.put("completed", mapper.completed(username));
        result.put("profile", mapper.profile(username));
        result.put(
                "completionNotice",
                "Demo completion records are synthetic and read-only. Real transcripts require"
                        + " staff import.");
        return result;
    }

    /** 原算法链路：读取资料 → 排序 → 一次性保存画像和结果。 */
    public List<Recommendation> recommend(String username, String interests, String goals, int topK)
            throws Exception {
        List<Recommendation> result =
                new Recommender()
                        .rank(catalog(), mapper.completed(username), interests + " " + goals, topK);
        historyWriter.save(username, interests, goals, result);
        return result;
    }

    /** 模型辅助链路同样由服务端读取成绩；无论成功还是回退，只保存一次结果。 */
    public RecommendationOutcome assisted(String username, String interests, String goals, int topK)
            throws Exception {
        // 先完成可能较慢的外部请求，再交给事务组件保存结果。
        RecommendationOutcome result =
                ai.recommend(catalog(), mapper.completed(username), interests + " " + goals, topK);
        historyWriter.save(username, interests, goals, result);
        return result;
    }

    /** 旧记录保存数组，新记录保存模式信封；在这里统一成前端可读取的 results。 */
    public List<Map<String, Object>> history(String username) throws Exception {
        List<Map<String, Object>> rows = mapper.history(username);
        for (Map<String, Object> row : rows) {
            com.fasterxml.jackson.databind.JsonNode saved =
                    json.readTree((String) row.remove("resultsJson"));
            row.put("results", saved.isObject() ? saved.path("results") : saved);
            row.put("mode", saved.isObject() ? saved.path("mode").asText() : "BASELINE");
            row.put("model", saved.isObject() ? saved.path("model").asText("") : "");
        }
        return rows;
    }
}
