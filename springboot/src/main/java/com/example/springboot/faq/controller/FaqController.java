package com.example.springboot.faq.controller;

import com.example.springboot.common.Result;
import com.example.springboot.faq.dto.FaqAnswerResponse;
import com.example.springboot.faq.dto.FaqAskRequest;
import com.example.springboot.faq.service.FaqRequestRunner;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Slf4j
@RestController
@RequestMapping("/faq")
public class FaqController {
    private static final Set<String> ROLES = new HashSet<>(Arrays.asList("stu", "dormManager", "admin"));
    private final FaqRequestRunner service;
    private final ObjectReader requestReader;

    public FaqController(FaqRequestRunner service, ObjectMapper objectMapper) {
        this.service = service;
        // This immutable reader tightens FAQ parsing without changing other endpoints.
        this.requestReader = objectMapper.readerFor(FaqAskRequest.class)
                .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    @PostMapping("/ask")
    public ResponseEntity<Result<FaqAnswerResponse>> ask(@RequestBody(required = false) String body,
                                                        HttpSession session) {
        Object identity = session.getAttribute("Identity");
        if (session.getAttribute("User") == null || identity == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Result.error("FAQ_UNAUTHENTICATED", "Please sign in to use FAQ assistance."));
        }
        if (!ROLES.contains(identity)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Result.error("FAQ_FORBIDDEN", "You do not have permission to use FAQ assistance."));
        }
        FaqAskRequest request;
        try {
            request = body == null ? null : requestReader.readValue(body);
        } catch (JsonProcessingException exception) {
            return invalidQuestion();
        }
        String question = request == null ? null : request.getQuestion();
        if (question == null) {
            return invalidQuestion();
        }
        question = question.replaceAll("(?U)^\\s+|\\s+$", "");
        if (question.isEmpty() || question.length() > 1000) {
            return invalidQuestion();
        }
        return ResponseEntity.ok(new Result<>("0", "Success", service.ask(question)));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<FaqAnswerResponse>> invalidQuestion() {
        return ResponseEntity.badRequest().body(Result.error("FAQ_INVALID_QUESTION",
                "Please provide a question between 1 and 1000 characters."));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Result<FaqAnswerResponse>> unavailable(DataAccessException exception) {
        log.warn("FAQ processing unavailable ({})", exception.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Result.error("FAQ_UNAVAILABLE",
                "FAQ service is temporarily unavailable. Please try again later."));
    }
}
