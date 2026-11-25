package com.example.springboot.faq.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.Getter;

@Getter
public class FaqChatRequest {
    private String question;
    private String conversationId;

    @JsonSetter("question")
    public void setQuestion(Object value) { question = string(value); }

    @JsonSetter("conversationId")
    public void setConversationId(Object value) { conversationId = string(value); }

    private String string(Object value) {
        if (value != null && !(value instanceof String)) {
            throw new IllegalArgumentException("Chat fields must be strings.");
        }
        return (String) value;
    }
}
