package com.example.springboot.faq.llm;

import com.example.springboot.faq.dto.FaqAnswerResponse;

/** Session-only bounded context. The question is resolved before storing successful follow-ups. */
public final class ConversationTurn {
    private final String question;
    private final String answer;
    private final String mode;
    private final boolean startsTopic;

    public ConversationTurn(String question, FaqAnswerResponse result) {
        this(question, result, false);
    }

    public ConversationTurn(String question, FaqAnswerResponse result, boolean startsTopic) {
        if (question == null || question.isEmpty() || question.length() > 1000) {
            throw new IllegalArgumentException("Invalid conversation question.");
        }
        this.question = question;
        this.mode = result.getMode();
        this.startsTopic = startsTopic;
        String text = result.isMatched() ? result.getAnswer() : result.getGuidance();
        int end = Math.min(text.length(), 2000);
        if (end > 0 && end < text.length() && Character.isHighSurrogate(text.charAt(end - 1))
                && Character.isLowSurrogate(text.charAt(end))) end--;
        this.answer = text.substring(0, end);
    }

    public String getQuestion() { return question; }
    public String getAnswer() { return answer; }
    public String getMode() { return mode; }
    public boolean isStartsTopic() { return startsTopic; }
}
