package com.example.springboot.faq.dto;

import lombok.Value;

/** Source metadata is projected from retrieved knowledge, never supplied by the model. */
@Value
public class FaqSource {
    Long faqId;
    String sourceTitle;
    String sourceUrl;
}
