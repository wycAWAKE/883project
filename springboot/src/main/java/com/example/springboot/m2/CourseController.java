package com.example.springboot.m2;

import com.example.springboot.common.Result;
import com.fasterxml.jackson.databind.*;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletRequest;
import java.util.*;

/**
 * M2 的 HTTP 入口：读取已验证的身份、校验请求，再把业务工作交给 CourseService。
 *
 * <p>这里不执行 SQL、不直接调用 DeepSeek，也不接受客户端指定用户或已修课程。 浏览器请求的 /api 前缀由 Vite 代理处理，后端实际路径从 /courses 开始。
 * 接口、请求样例与验证意图见 CODE_GUIDE.md#coursecontroller。
 */
@RestController
@RequestMapping("/courses")
public class CourseController {
    private final CourseService service;

    public CourseController(CourseService service, ObjectMapper unused) {
        // ObjectMapper 参数保留现有构造调用兼容性；请求解析由 Spring 完成。
        this.service = service;
    }

    private <T> Result<T> ok(T value) {
        return new Result<>("0", "Success", value);
    }

    /** 身份来自 CourseSessionInterceptor 写入的属性，不读取请求体里的账号。 */
    private String username(HttpServletRequest request) {
        return (String) request.getAttribute("m2.username");
    }

    /** 返回启用课程的公开资料和先修证据；不表示每门课都能被当前学生推荐。 */
    @GetMapping("/catalog")
    public Result<List<Course>> catalog() {
        return ok(service.catalog());
    }

    /** 页面初始化时读取个人资料和安全令牌；所有业务请求仍需有效 Session。 */
    @GetMapping("/me")
    public Result<Map<String, Object>> me(HttpServletRequest request) {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> profile = service.me(username(request));
        if (profile != null) result.putAll(profile);
        result.put("csrfToken", request.getAttribute("m2.csrf"));
        return ok(result);
    }

    /** 只按服务端确定的当前用户读取历史，忽略客户端伪造的用户查询参数。 */
    @GetMapping("/history")
    public Result<List<Map<String, Object>>> history(HttpServletRequest request) throws Exception {
        return ok(service.history(username(request)));
    }

    /** 兼容原有返回数组的推荐接口；不会调用外部大模型。 */
    @PostMapping("/recommendations")
    public Result<List<Recommendation>> recommend(
            @RequestBody JsonNode body, HttpServletRequest request) throws Exception {
        Inputs input = validate(body);
        return ok(service.recommend(username(request), input.interests, input.goals, input.topK));
    }

    /** 返回包含结果、实际模式和原因的对象，以便页面明确显示回退。 */
    @PostMapping("/recommendations/assisted")
    public Result<RecommendationOutcome> assisted(
            @RequestBody JsonNode body, HttpServletRequest request) throws Exception {
        Inputs input = validate(body);
        return ok(service.assisted(username(request), input.interests, input.goals, input.topK));
    }

    /** 两个推荐接口复用同一套已校验参数，防止两个入口的规则逐渐分叉。 */
    private static class Inputs {
        String interests, goals;
        int topK;

        Inputs(String i, String g, int k) {
            interests = i;
            goals = g;
            topK = k;
        }
    }

    /** 只允许兴趣、目标和数量；未知字段直接拒绝，避免被误认为已支持身份或成绩写入。 */
    private Inputs validate(JsonNode body) {
        if (!body.isObject()) throw new CourseException(400, "Provide a JSON object.");
        Set<String> allowed = new HashSet<>(Arrays.asList("interests", "goals", "topK"));
        Iterator<String> names = body.fieldNames();
        while (names.hasNext())
            if (!allowed.contains(names.next()))
                throw new CourseException(
                        400,
                        "Unknown field. Identity and completed courses are managed by the server.");
        String interests = text(body, "interests", true, 500),
                goals = text(body, "goals", false, 300);
        JsonNode k = body.get("topK");
        int topK = 3;
        if (k != null) {
            if (!k.isIntegralNumber()
                    || !k.canConvertToInt()
                    || k.intValue() < 1
                    || k.intValue() > 3)
                throw new CourseException(400, "topK must be an integer between 1 and 3.");
            topK = k.intValue();
        }
        return new Inputs(interests, goals, topK);
    }

    /** 去掉首尾空白，再校验文本类型、必填与字符长度；不自动把数字转换成文字。 */
    private String text(JsonNode body, String key, boolean required, int limit) {
        JsonNode value = body.get(key);
        if (value == null && !required) return "";
        if (value == null || !value.isTextual())
            throw new CourseException(400, key + " must be text.");
        String s = value.textValue().trim();
        if ((required && s.isEmpty()) || s.length() > limit)
            throw new CourseException(
                    400,
                    key
                            + " must contain "
                            + (required ? "1" : "0")
                            + " to "
                            + limit
                            + " characters.");
        return s;
    }
}
