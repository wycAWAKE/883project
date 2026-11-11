package com.example.springboot.m2;

import com.example.springboot.common.Result;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * M2 的统一错误出口：把控制器调用期间抛出的异常转换成 {code, msg, data}。
 *
 * <p>预期业务错误保留状态；无法解析的 JSON 返回 400；其他错误对页面使用通用 500 提示。 此 advice 限定在 M2 控制器所在包，避免无意改变其他成员的错误合同。
 * 处理范围与限制见 CODE_GUIDE.md#courseadvice。
 */
@RestControllerAdvice(basePackageClasses = CourseController.class)
public class CourseAdvice {
    private static final Logger LOG = LoggerFactory.getLogger(CourseAdvice.class);

    /** 保留拦截器或参数校验给出的状态与英文用户提示。 */
    @ExceptionHandler(CourseException.class)
    public ResponseEntity<Result<Object>> expected(CourseException e) {
        return ResponseEntity.status(e.getStatus())
                .body(Result.error(String.valueOf(e.getStatus()), e.getMessage()));
    }

    /** 请求尚未成为 JsonNode 时的解析错误，例如缺少引号的 JSON。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Object>> malformed() {
        return ResponseEntity.badRequest()
                .body(Result.error("400", "Provide a valid JSON object."));
    }

    /** 记录服务端异常以便排查，但不给前端暴露 SQL、堆栈或连接配置。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Object>> failure(Exception e) {
        LOG.error("M2 request failed; no personalized payload logged", e);
        return ResponseEntity.status(500)
                .body(Result.error("500", "Course service is unavailable. Please retry later."));
    }
}
