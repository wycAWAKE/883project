package com.example.springboot.m2;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;

/**
 * DeepSeek 辅助接口的结果信封：即使结果为空，也能告诉页面实际使用了哪种模式。
 *
 * <p>原算法接口仍返回 {@code List<Recommendation>}，没有改成这个结构，以保持旧接口兼容。 详细说明见 CODE_GUIDE.md#recommendationoutcome。
 */
@Data
@AllArgsConstructor
public class RecommendationOutcome {
    private List<Recommendation> results;

    /** LLM_ASSISTED、FALLBACK 或 ELIGIBILITY_FILTER，反映实际执行结果。 */
    private String mode;

    /** 供代码识别的原因，如 NOT_CONFIGURED；与面向用户的 message 分开。 */
    private String reason;

    private String message;

    /** 模型辅助成功时记录配置的模型名；原算法回退时为 null。 */
    private String model;
}
