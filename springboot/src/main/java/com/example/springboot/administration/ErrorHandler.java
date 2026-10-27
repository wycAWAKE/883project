package com.example.springboot.administration;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.DataIntegrityViolationException;
import org.slf4j.Logger;import org.slf4j.LoggerFactory;
@RestControllerAdvice(assignableTypes=AdminController.class)
public class ErrorHandler {
 private static final Logger log=LoggerFactory.getLogger(ErrorHandler.class);
 private ResponseEntity<com.example.springboot.common.Result<Void>> response(int code,String message) {
  return ResponseEntity.status(code).body(com.example.springboot.common.Result.error(String.valueOf(code),message));
 }
 @ExceptionHandler(Api.Problem.class) ResponseEntity<com.example.springboot.common.Result<Void>> problem(Api.Problem e) {return response(e.status,e.getMessage());}
 @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<com.example.springboot.common.Result<Void>> invalid(MethodArgumentNotValidException e) {return response(400,"Check the required fields, values and text lengths.");}
 @ExceptionHandler(HttpMessageNotReadableException.class) ResponseEntity<com.example.springboot.common.Result<Void>> unreadable(Exception e) {return response(400,"Invalid request body.");}
 @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class) ResponseEntity<com.example.springboot.common.Result<Void>> badType(Exception e) {return response(400,"Invalid parameter type.");}
 @ExceptionHandler(MaxUploadSizeExceededException.class) ResponseEntity<com.example.springboot.common.Result<Void>> large(Exception e) {return response(413,"Each file must be 5 MB or smaller.");}
 @ExceptionHandler(org.springframework.dao.DuplicateKeyException.class) ResponseEntity<com.example.springboot.common.Result<Void>> conflict(Exception e) {return response(409,"The record changed or the request was repeated. Refresh and try again.");}
 @ExceptionHandler(DataIntegrityViolationException.class) ResponseEntity<com.example.springboot.common.Result<Void>> integrity(Exception e) {log.error("Database integrity failure",e);return response(500,"The database could not save this operation. No changes were committed.");}
 @ExceptionHandler(Exception.class) ResponseEntity<com.example.springboot.common.Result<Void>> other(Exception e) {log.error("Request failed",e);return response(500,"The operation failed. Please try again.");}
}
