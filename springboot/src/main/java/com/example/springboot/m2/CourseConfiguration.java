package com.example.springboot.m2;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

/**
 * 把 M2 的数据库 Mapper 和权限拦截器接入现有 Spring Boot 应用。
 *
 * <p>如果没有此配置，CourseMapper 无法按当前方式被扫描，/courses/** 也不会经过 CourseSessionInterceptor。该配置不创建另一套登录系统。说明见
 * CODE_GUIDE.md#courseconfiguration。
 */
@Configuration
@MapperScan("com.example.springboot.m2.mapper")
public class CourseConfiguration implements WebMvcConfigurer {
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 统一覆盖现有和今后新增的课程子路径，避免只保护页面而漏掉 API。
        registry.addInterceptor(new CourseSessionInterceptor()).addPathPatterns("/courses/**");
    }
}
