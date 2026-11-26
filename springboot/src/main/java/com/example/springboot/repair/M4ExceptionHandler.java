package com.example.springboot.repair;

import com.example.springboot.common.Result;
import com.example.springboot.controller.RepairTicketController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

// Keep legacy controller response contracts unchanged.
@RestControllerAdvice(assignableTypes = RepairTicketController.class)
public class M4ExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(M4ExceptionHandler.class);
    @ExceptionHandler(M4ApiException.class)
    public ResponseEntity<Result<?>> business(M4ApiException ex) {
        return error(ex.getStatus(), ex.getMessage());
    }
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Result<?>> malformed(Exception ex) {
        return error(400, "Invalid request fields or parameter types.");
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<?>> unexpected(Exception ex) {
        // Do not return SQL statements or exception contents to the browser.
        log.error("M4 operation failed ({})", ex.getClass().getSimpleName());
        return error(500, "The ticket operation could not be completed. Please try again.");
    }
    private ResponseEntity<Result<?>> error(int status, String message) {
        return ResponseEntity.status(status).body(Result.error(String.valueOf(status), message));
    }
}
