package com.example.springboot.m2.mapper;

import com.example.springboot.m2.Course;
import org.apache.ibatis.annotations.*;
import java.util.*;

/**
 * M2 的数据库访问接口，MyBatis 根据注解 SQL 生成实现，无需手写实现类。
 *
 * <p>此层只读写数据，不判断登录、计算推荐或调用模型；username 必须由上层取自可信 Session。 表、方法和事务边界的逐项说明见 ../CODE_GUIDE.md#coursemapper。
 */
public interface CourseMapper {
    /** 只读取启用的课程基本字段；分组先修条件由另一个查询获取。 */
    @Select(
            "SELECT code,source_course_code AS"
                + " sourceCourseCode,title,description,topics,source_year AS sourceYear,source_url"
                + " AS sourceUrl,syllabus_url AS syllabusUrl,source_sha256 AS"
                + " sourceSha256,license_url AS licenseUrl,instructors_json AS"
                + " instructorsJson,prerequisite_status AS prerequisiteStatus,prerequisite_evidence"
                + " AS prerequisiteEvidence,active FROM m2_course WHERE active=1 ORDER BY code")
    List<Course> catalog();

    /** 返回课程编号、组号、先修编号的扁平行，由 CourseService 重建 AND / OR 分组。 */
    @Select(
            "SELECT course_code AS courseCode,group_no AS groupNo,prerequisite_code AS"
                    + " prerequisiteCode FROM m2_prerequisite ORDER BY"
                    + " course_code,group_no,prerequisite_code")
    List<Map<String, Object>> prerequisites();

    /** 受控已修记录，不接受页面临时声明的学习经历替代。 */
    @Select(
            "SELECT course_code FROM m2_completed WHERE student_username=#{username} ORDER BY"
                    + " course_code")
    Set<String> completed(String username);

    /** 最近保存的兴趣和学习目标，用于重新进入页面时回填。 */
    @Select("SELECT interests,goals FROM m2_profile WHERE student_username=#{username}")
    Map<String, Object> profile(String username);

    /** 同一学生有则更新、无则插入；必须与 addHistory 一起由事务保存组件调用。 */
    @Insert(
            "INSERT INTO m2_profile(student_username,interests,goals)"
                + " VALUES(#{username},#{interests},#{goals}) ON DUPLICATE KEY UPDATE"
                + " interests=VALUES(interests),goals=VALUES(goals),updated_at=CURRENT_TIMESTAMP(6)")
    int saveProfile(
            @Param("username") String username,
            @Param("interests") String interests,
            @Param("goals") String goals);

    /** 追加结果快照，不覆盖旧结果；JSON 可为旧数组或新模式信封。 */
    @Insert(
            "INSERT INTO m2_history(student_username,query_text,results_json)"
                    + " VALUES(#{username},#{query},#{json})")
    int addHistory(
            @Param("username") String username,
            @Param("query") String query,
            @Param("json") String json);

    /** 仅返回该学生最近二十条，按递增 id 的倒序保证稳定次序。 */
    @Select(
            "SELECT id,query_text AS queryText,results_json AS resultsJson,created_at AS createdAt"
                + " FROM m2_history WHERE student_username=#{username} ORDER BY id DESC LIMIT 20")
    List<Map<String, Object>> history(String username);
}
