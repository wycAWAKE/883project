package com.example.springboot.m2;

import com.example.springboot.entity.Student;
import com.example.springboot.security.SessionSecurity;
import org.springframework.web.servlet.HandlerInterceptor;
import javax.servlet.http.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 在课程控制器之前验证现有登录 Session、学生角色和写请求的 CSRF 令牌。
 *
 * <p>验证通过后才向 request 写入 m2.username / m2.csrf，供控制器读取可信身份。 本类既不查询课程，也不创建新的认证机制。顺序与失败场景见
 * CODE_GUIDE.md#coursesessioninterceptor。
 */
public class CourseSessionInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 课程接口可能返回画像或个人历史，要求浏览器不要缓存这些响应。
        response.setHeader("Cache-Control", "no-store");
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("Identity") == null)
            throw new CourseException(401, "Please sign in again.");
        if (!"stu".equals(session.getAttribute("Identity")))
            throw new CourseException(
                    403, "Course recommendations are available to students only.");
        Object user = session.getAttribute("User");
        if (!(user instanceof Student)
                || ((Student) user).getUsername() == null
                || ((Student) user).getUsername().trim().isEmpty())
            throw new CourseException(401, "Please sign in again.");
        // 令牌绑定当前 Session 身份。GET 用于读取；其他方法必须携带相同令牌。
        String token = SessionSecurity.token(session);
        if (!"GET".equals(request.getMethod())) {
            String provided = request.getHeader("X-M2-CSRF");
            if (provided == null
                    || !MessageDigest.isEqual(
                            token.getBytes(StandardCharsets.UTF_8),
                            provided.getBytes(StandardCharsets.UTF_8)))
                throw new CourseException(
                        403, "Security token expired. Reload the page and retry.");
        }
        request.setAttribute("m2.username", ((Student) user).getUsername());
        request.setAttribute("m2.csrf", token);
        return true;
    }
}
