package com.example.springboot.m2;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;

/**
 * 一条推荐结果：把课程、匹配分数、匹配词和理由一起交给页面或历史保存组件。
 *
 * <p>它不负责排序或判断先修资格。Lombok 生成字段访问器和四参数构造器。 详细说明见 CODE_GUIDE.md#recommendation。
 */
@Data
@AllArgsConstructor
public class Recommendation {
    private Course course;

    /** 原算法使用 TF-IDF 余弦相似度；DeepSeek 直接选择不产生该分数，值为 null。 */
    private Double score;

    /** 原算法的匹配词；DeepSeek 直接选择时为空列表。 */
    private List<String> matchingTerms;

    /** 原算法使用匹配词模板；辅助模式可能使用经过结构校验的模型生成理由。 */
    private String explanation;
}
