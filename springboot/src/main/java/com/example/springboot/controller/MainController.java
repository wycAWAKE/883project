package com.example.springboot.controller;

import com.example.springboot.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpSession;

@RestController
@RequestMapping("/main")
public class MainController {

    /**
     * 获取身份信息
     */
    @GetMapping("/loadIdentity")
    public Result<?> loadIdentity(HttpSession session) {
        // 答辩链路-读取Session身份：浏览器带 JSESSIONID 后，后端能找到对应 Session；下一步从 Session 里取 Identity 返回给前端。
        Object identity = session.getAttribute("Identity");

        if (identity != null) {
            return Result.success(identity);
        } else {
            return Result.error("-1", "加载失败");
        }
    }

    /**
     * 获取个人信息
     */
    @GetMapping("/loadUserInfo")
    public Result<?> loadUserInfo(HttpSession session) {
        // 答辩链路-读取Session用户：根据 JSESSIONID 找到服务器端 Session；下一步从 Session 里取 User 返回给前端。
        Object User = session.getAttribute("User");

        if (User != null) {
            return Result.success(User);
        } else {
            return Result.error("-1", "加载失败");
        }
    }

    /**
     * 退出登录
     */
    @GetMapping("/signOut")
    public Result<?> signOut(HttpSession session) {
        // 答辩链路-退出登录：清除服务器 Session 中的 User 和 Identity；下一步前端重新登录才会建立新的登录状态。
        session.removeAttribute("User");
        session.removeAttribute("Identity");
        return Result.success();
    }
}
