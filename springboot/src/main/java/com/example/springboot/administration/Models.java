package com.example.springboot.administration;
import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
/** Public persistence fields keep this small coursework module free of code-generation tools. */
public final class Models {
 private Models() {}
 @TableName("sc_application") public static class Application {
  @TableId(type=IdType.INPUT) public String id;
  public String referenceNo; public String applicantId; public String applicantName; public String studentNumber;
  public String programme; public String email; public String applicationType; public String purpose;
  public String documentLanguage; public String deliveryMethod; public String notes; public String status;
  public String latestReason; public Integer version; public Integer revision; @com.fasterxml.jackson.annotation.JsonIgnore public String requestKey;
  @com.fasterxml.jackson.annotation.JsonIgnore public String requestDigest; public LocalDateTime createdAt; public LocalDateTime updatedAt;
 }
 @TableName("sc_history") public static class History {
  @TableId(type=IdType.INPUT) public String id;
  public String applicationId; public String actorId; public String actorName; public String action;
  public String fromStatus; public String toStatus; public String reason; public Integer revision;
  public String snapshotJson; public LocalDateTime createdAt;
 }
 @TableName("sc_notification") public static class Notice {
  @TableId(type=IdType.INPUT) public String id;
  public String userId; public String applicationId; public String title; public String content;
  public Boolean isRead; public LocalDateTime createdAt;
 }
 @lombok.Data @lombok.NoArgsConstructor @lombok.AllArgsConstructor public static class User {
  private String id; public String id(){return id;}
  private String username; public String username(){return username;}
  private String fullName; public String fullName(){return fullName;}
  private String studentNumber; public String studentNumber(){return studentNumber;}
  private String programme; public String programme(){return programme;}
  private String email; public String email(){return email;}
  private String role; public String role(){return role;}
 }
 @lombok.Data @lombok.NoArgsConstructor @lombok.AllArgsConstructor public static class Attachment {
  private String id; public String id(){return id;}
  private String filename; public String filename(){return filename;}
  private String contentType; public String contentType(){return contentType;}
  private long fileSize; public long fileSize(){return fileSize;}
  private boolean active; public boolean active(){return active;}
  private LocalDateTime createdAt; public LocalDateTime createdAt(){return createdAt;}
 }
 @lombok.Data @lombok.NoArgsConstructor @lombok.AllArgsConstructor public static class FileData {
  private String filename; public String filename(){return filename;}
  private String contentType; public String contentType(){return contentType;}
  private byte[] bytes; public byte[] bytes(){return bytes;}
 }
}
