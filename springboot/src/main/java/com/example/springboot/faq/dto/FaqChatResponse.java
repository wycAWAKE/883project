package com.example.springboot.faq.dto;

import lombok.Getter;

/** Same public answer contract as /ask, with a server-issued conversation identifier. */
@Getter
public class FaqChatResponse extends FaqAnswerResponse {
    private final String conversationId;

    public FaqChatResponse(String conversationId, FaqAnswerResponse answer) {
        super(answer.isMatched(), answer.getFaqId(), answer.getAnswer(), answer.getSourceTitle(),
                answer.getSourceUrl(), answer.getScore(), answer.getGuidance(), answer.getMode(),
                answer.getSources(), answer.getNotice());
        this.conversationId = conversationId;
    }
}
