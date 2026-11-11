package com.example.springboot.m2;

/**
 * M2 中预期的请求错误：携带 HTTP 状态，由 CourseAdvice 转成统一响应。
 *
 * <p>例如输入无效用 400、会话失效用 401、无权限或 CSRF 错误用 403。 它不负责发送响应或记录日志。说明见 CODE_GUIDE.md#courseexception。
 */
public class CourseException extends RuntimeException {
    private final int status;

    public CourseException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
