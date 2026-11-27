package com.example.springboot.administration;
import com.example.springboot.administration.Models.*;
import com.example.springboot.administration.mapper.*;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.LocalDateTime;
import java.util.*;
import java.security.MessageDigest;
import static com.example.springboot.administration.Api.require;
@Service
public class ApplicationService {
 private final ApplicationMapper applications;private final HistoryMapper histories;private final NoticeMapper notices;
 private final AttachmentService attachments;private final Auth auth;private final JdbcTemplate jdbc;private final ObjectMapper json;
 public ApplicationService(ApplicationMapper applications,HistoryMapper histories,NoticeMapper notices,AttachmentService attachments,Auth auth,JdbcTemplate jdbc,ObjectMapper json) {
  this.applications=applications;this.histories=histories;this.notices=notices;this.attachments=attachments;this.auth=auth;this.jdbc=jdbc;this.json=json;
 }
 @lombok.Data @lombok.NoArgsConstructor @lombok.AllArgsConstructor public static class Detail {
  private Application application; public Application application(){return application;}
  private List<Attachment> attachments; public List<Attachment> attachments(){return attachments;}
  private List<History> history; public List<History> history(){return history;}
 }
 public Application accessible(User user,String id) {
  Application app=applications.selectById(id);
  require(app!=null && ("STAFF".equals(user.role()) || app.applicantId.equals(user.id())),404,"Application not found.");return app;
 }
 public Detail detail(User user,String id) {
  Application app=accessible(user,id);
  return new Detail(app,attachments.list(id),histories.selectList(new QueryWrapper<History>().eq("application_id",id).orderByAsc("created_at","id")));
 }
 public Api.Page<Application> list(User user,String status,String search,int page,int size) {
  require(page>=1 && page<=100000 && size>=1 && size<=50,400,"Invalid page number or page size.");
  require(status==null || status.trim().isEmpty() || new HashSet<>(Arrays.asList("PENDING","RETURNED","APPROVED","REJECTED")).contains(status),400,"Invalid application status.");
  require(search==null || search.length()<=100,400,"Search text must not exceed 100 characters.");
  QueryWrapper<Application> q=new QueryWrapper<>();
  if(!"STAFF".equals(user.role()))q.eq("applicant_id",user.id());
  if(status!=null && !status.trim().isEmpty())q.eq("status",status);
  if(search!=null && !search.trim().isEmpty())q.and(w->w.like("reference_no",search.trim()).or().like("applicant_name",search.trim()).or().like("student_number",search.trim()));
  long total=applications.selectCount(q);
  q.orderByDesc("updated_at","id").last("LIMIT "+size+" OFFSET "+((page-1)*size));
  return new Api.Page<>(applications.selectList(q),total,page,size);
 }
 public Map<String,Long> summary(User user) {
  Map<String,Long> result=new LinkedHashMap<>();for(String state:Arrays.asList("PENDING","RETURNED","APPROVED","REJECTED"))result.put(state,0L);
  String sql="SELECT status,COUNT(*) AS cnt FROM sc_application";
  List<Map<String,Object>> rows="STAFF".equals(user.role())?jdbc.queryForList(sql+" GROUP BY status"):jdbc.queryForList(sql+" WHERE applicant_id=? GROUP BY status",user.id());
  for(Map<String,Object> row:rows)result.put((String)row.get("status"),((Number)row.get("cnt")).longValue());return result;
 }
 @Transactional public Detail submit(User user,Api.Submission req) {
  auth.role(user,"STUDENT");validate(req);String digest=digest(req);
  Application existing=applications.selectOne(new QueryWrapper<Application>().eq("request_key",req.requestKey()));
  if(existing!=null) {
   require(existing.applicantId.equals(user.id()) && existing.requestDigest.equals(digest),409,"This submission key is already in use. Start a new submission.");
   return detail(user,existing.id);
  }
  Application app=new Application();app.id=UUID.randomUUID().toString();
  app.referenceNo="SC-"+app.id.substring(0,18).replace("-", "").toUpperCase(Locale.ROOT);
  app.applicantId=user.id();app.applicantName=user.fullName();app.studentNumber=user.studentNumber();app.programme=user.programme();app.email=user.email();
  apply(app,req);app.status="PENDING";app.version=0;app.revision=1;app.requestKey=req.requestKey();app.requestDigest=digest;
  app.createdAt=LocalDateTime.now();app.updatedAt=app.createdAt;applications.insert(app);
  attachments.bind(user,app.id,req.attachmentIds(),"TRANSCRIPT".equals(app.applicationType));
  record(app,user,"SUBMIT",null,"Application submitted for review.");return detail(user,app.id);
 }
 @Transactional public Detail resubmit(User user,String id,Api.Submission req) {
  auth.role(user,"STUDENT");validate(req);Application app=accessible(user,id);
  require(req.version()!=null,400,"The application version is missing. Refresh and try again.");
  require("RETURNED".equals(app.status),409,"Only returned applications can be revised and resubmitted.");
  int changed=applications.update(null,new UpdateWrapper<Application>()
   .set("application_type",req.applicationType()).set("purpose",req.purpose().trim()).set("document_language",req.documentLanguage())
   .set("delivery_method",req.deliveryMethod()).set("notes",clean(req.notes())).set("status","PENDING").set("latest_reason",null)
   .setSql("version=version+1,revision=revision+1").set("updated_at",LocalDateTime.now())
   .eq("id",id).eq("applicant_id",user.id()).eq("status","RETURNED").eq("version",req.version()));
  require(changed==1,409,"The application changed. Refresh and try again.");
  app=applications.selectById(id);attachments.bind(user,id,req.attachmentIds(),"TRANSCRIPT".equals(app.applicationType));
  record(app,user,"RESUBMIT","RETURNED","Application revised and resubmitted.");return detail(user,id);
 }
 @Transactional public Detail decide(User user,String id,Api.Decision req) {
  auth.role(user,"STAFF");Application app=accessible(user,id);require("PENDING".equals(app.status),409,"This application has already been reviewed. Refresh the page.");
  String reason=clean(req.reason());require("APPROVE".equals(req.action()) || !reason.trim().isEmpty(),400,"A reason is required when returning or rejecting an application.");
  String target; switch(req.action()) {case "RETURN": target="RETURNED"; break; case "APPROVE": target="APPROVED"; break; case "REJECT": target="REJECTED"; break; default: throw new Api.Problem(400,"Invalid review action.");}
  int changed=applications.update(null,new UpdateWrapper<Application>().set("status",target).set("latest_reason",reason)
   .setSql("version=version+1").set("updated_at",LocalDateTime.now()).eq("id",id).eq("status","PENDING").eq("version",req.version()));
  require(changed==1,409,"Another reviewer updated this application. Refresh the page.");
  app=applications.selectById(id);record(app,user,req.action(),"PENDING",reason);return detail(user,id);
 }
 private void validate(Api.Submission req) {
  require(req.purpose().trim().length()>=5,400,"The purpose must contain at least 5 characters after trimming.");
  try {UUID.fromString(req.requestKey());}catch(IllegalArgumentException e){throw new Api.Problem(400,"Invalid submission key.");}
 }
 private String clean(String value) {return value==null?"":value.trim();}
 private void apply(Application app,Api.Submission req) {
  app.applicationType=req.applicationType();app.purpose=req.purpose().trim();app.documentLanguage=req.documentLanguage();app.deliveryMethod=req.deliveryMethod();app.notes=clean(req.notes());
 }
 private String digest(Api.Submission req) {
  try {return hex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(req)));}
  catch(Exception e){throw new IllegalStateException(e);}
 }
 private String hex(byte[] bytes) {StringBuilder value=new StringBuilder();for(byte b:bytes)value.append(String.format("%02x",b & 255));return value.toString();}
 private void record(Application app,User actor,String action,String from,String reason) {
  History h=new History();h.id=UUID.randomUUID().toString();h.applicationId=app.id;h.actorId=actor.id();h.actorName=actor.fullName();
  h.action=action;h.fromStatus=from;h.toStatus=app.status;h.reason=reason;h.revision=app.revision;h.createdAt=LocalDateTime.now();
  try {
   Map<String,Object> snapshot=new LinkedHashMap<>();snapshot.put("applicationType",app.applicationType);snapshot.put("purpose",app.purpose);
   snapshot.put("documentLanguage",app.documentLanguage);snapshot.put("deliveryMethod",app.deliveryMethod);snapshot.put("notes",app.notes);
   snapshot.put("attachments",attachments.list(app.id).stream().filter(Attachment::active).collect(java.util.stream.Collectors.toList()));h.snapshotJson=json.writeValueAsString(snapshot);
  }catch(Exception e){throw new IllegalStateException(e);}
  histories.insert(h);
  Notice n=new Notice();n.id=UUID.randomUUID().toString();n.userId=app.applicantId;n.applicationId=app.id;n.isRead=false;n.createdAt=h.createdAt;
  String state; switch(app.status){case "PENDING":state="Pending review";break;case "RETURNED":state="More information required";break;case "APPROVED":state="Approved";break;default:state="Rejected";}
  n.title=app.referenceNo+" · "+state;n.content=reason.trim().isEmpty()?"Application status: "+state:reason;notices.insert(n);
 }
}
