package com.example.springboot.faq.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("faq_entry")
public class FaqEntry {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String question;
    private String answer;
    private String category;
    private String keywords;
    private String sourceTitle;
    private String sourceUrl;
    private Boolean enabled;
    private LocalDateTime updatedAt;
}
