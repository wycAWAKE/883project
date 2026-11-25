package com.example.springboot.faq.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.Data;

@Data
public class FaqAskRequest {
    private String question;

    @JsonSetter("question")
    public void setQuestion(Object value) {
        // Preserve the string contract instead of Jackson's default scalar coercion.
        if (value != null && !(value instanceof String)) {
            throw new IllegalArgumentException("Question must be a string.");
        }
        this.question = (String) value;
    }
}
