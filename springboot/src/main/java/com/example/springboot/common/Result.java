package com.example.springboot.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {
    private String code;
    private String msg;
    private T data;

    public static <T> Result<T> success() {
        return new Result<>("0", "成功", null);
    }

    public static <T> Result<T> success(T data) {
        // 答辩链路-统一JSON格式：Controller 调 Result.success(page) 后返回 {code:'0', msg:'成功', data:page}；下一步前端 request.js 取 response.data。
        return new Result<>("0", "成功", data);
    }

    public static <T> Result<T> error(String code, String msg) {
        return new Result<>(code, msg, null);
    }
}
