package com.example.springboot.faq.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.springboot.faq.entity.FaqEntry;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface FaqMapper extends BaseMapper<FaqEntry> {
    @Select("SELECT id, question, answer, category, keywords, source_title AS sourceTitle, "
            + "source_url AS sourceUrl, enabled, updated_at AS updatedAt "
            + "FROM faq_entry WHERE enabled = 1 ORDER BY id ASC")
    List<FaqEntry> findEnabled();
}
