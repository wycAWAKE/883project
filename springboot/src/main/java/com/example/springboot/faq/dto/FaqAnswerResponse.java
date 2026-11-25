package com.example.springboot.faq.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class FaqAnswerResponse {
    private boolean matched;
    private Long faqId;
    private String answer;
    private String sourceTitle;
    private String sourceUrl;
    private double score;
    private String guidance;
    private String mode;
    private List<FaqSource> sources;
    private String notice;
}
