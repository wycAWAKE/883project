package com.example.springboot.m2;

import lombok.Data;
import java.util.*;

/**
 * M2 的一门课程及其公开来源、先修规则；不代表某个学生的成绩或选课记录。
 *
 * <p>CourseMapper 从数据库读取基本字段，CourseService 再补上分组先修条件。Lombok @Data 生成 getter/setter 等方法，字段名也用于 API
 * JSON；修改字段名会影响前端和历史快照。
 *
 * <p>逐字段说明与样例：CODE_GUIDE.md#course。
 */
@Data
public class Course {
    /** 本项目用于关联、去重和资格检查的课程编号，如 6.0001。 */
    private String code;

    /** 来源站点原始编号；可能与本项目归一化后的 code 不同。 */
    private String sourceCourseCode;

    private String title;
    private String description;

    /** 数据库中的主题文本，参与 TF-IDF 检索；不是前端传入的学生兴趣。 */
    private String topics;

    private String sourceUrl;
    private String syllabusUrl;

    /** 下载的原始课程元数据 SHA-256，供数据来源校验使用。 */
    private String sourceSha256;

    private String licenseUrl;

    /** 教师名单的 JSON 字符串；前端解析后显示署名。 */
    private String instructorsJson;

    /** structured / no_formal_course / review_required，决定是否能自动检查资格。 */
    private String prerequisiteStatus;

    /** 已保存的先修原文或待审核说明，便于成员核对规则来源。 */
    private String prerequisiteEvidence;

    /** 来源课程的历史年份，不等于当前学年。 */
    private int sourceYear;

    private boolean active;

    /** 外层各组为 AND；内层编号为 OR，例如 [[A], [B, C]] = A AND (B OR C)。 */
    private List<List<String>> prerequisiteGroups = new ArrayList<>();
}
