package com.example.springboot.administration;
import javax.validation.constraints.*;
import java.util.List;
public final class Api {
 private Api() {}
 @lombok.Data @lombok.NoArgsConstructor public static class Submission {
  @NotBlank @Pattern(regexp="[0-9a-fA-F-]{36}") private String requestKey;
  @NotBlank @Pattern(regexp="ENROLMENT_CERTIFICATE|TRANSCRIPT") private String applicationType;
  @NotBlank @Size(min=5,max=1000) private String purpose;
  @NotBlank @Pattern(regexp="ENGLISH|CHINESE") private String documentLanguage;
  @NotBlank @Pattern(regexp="ELECTRONIC|PICKUP") private String deliveryMethod;
  @Size(max=1000) private String notes;
  @NotNull @Size(max=5) private List<@NotBlank String> attachmentIds;
  @Min(0) private Integer version;
  public String requestKey(){return requestKey;} public String applicationType(){return applicationType;}
  public String purpose(){return purpose;} public String documentLanguage(){return documentLanguage;}
  public String deliveryMethod(){return deliveryMethod;} public String notes(){return notes;}
  public List<String> attachmentIds(){return attachmentIds;} public Integer version(){return version;}
 }
 @lombok.Data @lombok.NoArgsConstructor public static class Decision {
  @NotBlank @Pattern(regexp="RETURN|APPROVE|REJECT") private String action;
  @Size(max=1000) private String reason;
  @NotNull @Min(0) private Integer version;
  public String action(){return action;} public String reason(){return reason;} public Integer version(){return version;}
 }
 @lombok.Data @lombok.AllArgsConstructor public static class Page<T> {
  private List<T> records;private long total;private int pageNum;private int pageSize;
 }
 public static class Problem extends RuntimeException {
  public final int status;
  public Problem(int status,String message){super(message);this.status=status;}
 }
 public static void require(boolean value,int status,String message){if(!value)throw new Problem(status,message);}
}
