package com.example.springboot.faq.controller;

import com.example.springboot.common.Result;
import com.example.springboot.faq.dto.FaqChatRequest;
import com.example.springboot.faq.dto.FaqChatResponse;
import com.example.springboot.faq.service.FaqConversationService;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.util.Locale;

@RestController
@RequestMapping("/faq/chat")
public class FaqChatController {
    private final FaqConversationService conversations;
    private final ObjectReader reader;

    public FaqChatController(FaqConversationService conversations, ObjectMapper mapper) {
        this.conversations = conversations;
        this.reader = mapper.readerFor(FaqChatRequest.class)
                .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    }

    @PostMapping
    public ResponseEntity<Result<FaqChatResponse>> chat(@RequestBody(required = false) String body, HttpSession session) {
        conversations.authorize(session);
        FaqChatRequest request;
        try { request = body == null ? null : reader.readValue(body); }
        catch (JsonProcessingException error) { return invalidRequest(); }
        if (request == null || request.getQuestion() == null) return invalidRequest();
        String question = request.getQuestion().replaceAll("(?U)^\\s+|\\s+$", "");
        String id = request.getConversationId();
        if (question.isEmpty() || question.length() > 1000 || (id != null && !id.matches(
                "(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))) return invalidRequest();
        return ResponseEntity.ok(new Result<>("0", "Success", conversations.chat(
                question, id == null ? null : id.toLowerCase(Locale.ROOT), session)));
    }

    @DeleteMapping("/current")
    public Result<Void> clear(HttpSession session) {
        conversations.clear(session);
        return new Result<>("0", "Success", null);
    }

    private ResponseEntity<Result<FaqChatResponse>> invalidRequest() {
        return ResponseEntity.badRequest().body(Result.error("FAQ_INVALID_CHAT_REQUEST", "Please provide a valid FAQ chat request."));
    }

    @ExceptionHandler(FaqConversationService.ConversationException.class)
    public ResponseEntity<Result<Void>> conversationError(FaqConversationService.ConversationException error) {
        return ResponseEntity.status(error.getStatus()).body(Result.error(error.getCode(), error.getMessage()));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Result<Void>> unavailable() {
        return ResponseEntity.status(503).body(Result.error("FAQ_UNAVAILABLE", "FAQ service is temporarily unavailable. Please try again later."));
    }
}
