package com.example.springboot.administration;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import java.io.IOException;
import static com.example.springboot.administration.Api.require;
@Service
public class AttachmentService {
 private final JdbcTemplate jdbc;private final Auth auth;
 public AttachmentService(JdbcTemplate jdbc,Auth auth) {this.jdbc=jdbc;this.auth=auth;}
 public Models.Attachment upload(Models.User user,MultipartFile file) throws IOException {
  auth.role(user,"STUDENT");
  require(file!=null && !file.isEmpty(),400,"Choose a supporting file.");require(file.getSize()<=5L*1024*1024,413,"Each file must be 5 MB or smaller.");
  String name=Optional.ofNullable(file.getOriginalFilename()).orElse("document").replace('\\','/');
  name=name.substring(name.lastIndexOf('/')+1).replaceAll("[\\p{Cntrl}]", "_");
  require(!name.trim().isEmpty() && name.length()<=160,400,"The filename must contain 1 to 160 characters.");
  byte[] data=file.getBytes();String lower=name.toLowerCase(Locale.ROOT);String type;
  if(lower.endsWith(".pdf") && starts(data,new byte[]{37,80,68,70,45})) type="application/pdf";
  else if(lower.endsWith(".png") && starts(data,new byte[]{(byte)137,80,78,71,13,10,26,10})) type="image/png";
  else if((lower.endsWith(".jpg")||lower.endsWith(".jpeg")) && starts(data,new byte[]{(byte)255,(byte)216,(byte)255})) type="image/jpeg";
  else throw new Api.Problem(400,"Upload a PDF, PNG or JPG whose file signature matches its extension.");
  Integer pending=jdbc.queryForObject("SELECT COUNT(*) FROM sc_attachment WHERE owner_id=? AND application_id IS NULL",Integer.class,user.id());
  require(pending<20,400,"You have 20 unsubmitted files. Remove unused files before uploading more.");
  String id=UUID.randomUUID().toString();LocalDateTime now=LocalDateTime.now();
  jdbc.update("INSERT INTO sc_attachment(id,owner_id,filename,content_type,file_size,file_data,active,created_at) VALUES(?,?,?,?,?,?,?,?)",id,user.id(),name,type,data.length,data,false,now);
  return new Models.Attachment(id,name,type,data.length,false,now);
 }
 private boolean starts(byte[] data,byte[] signature) {if(data.length<signature.length)return false;for(int i=0;i<signature.length;i++)if(data[i]!=signature[i])return false;return true;}
 public List<Models.Attachment> pending(Models.User user) {
  auth.role(user,"STUDENT");
  return jdbc.query("SELECT id,filename,content_type,file_size,active,created_at FROM sc_attachment WHERE owner_id=? AND application_id IS NULL ORDER BY created_at,id",(rs,n)->new Models.Attachment(rs.getString(1),rs.getString(2),rs.getString(3),rs.getLong(4),rs.getBoolean(5),rs.getTimestamp(6).toLocalDateTime()),user.id());
 }
 public List<Models.Attachment> list(String appId) {
  return jdbc.query("SELECT id,filename,content_type,file_size,active,created_at FROM sc_attachment WHERE application_id=? ORDER BY created_at,id",(rs,n)->new Models.Attachment(rs.getString(1),rs.getString(2),rs.getString(3),rs.getLong(4),rs.getBoolean(5),rs.getTimestamp(6).toLocalDateTime()),appId);
 }
 @Transactional public void bind(Models.User user,String applicationId,List<String> ids,boolean required) {
  require(new HashSet<>(ids).size()==ids.size(),400,"A file cannot appear twice.");require(!required || !ids.isEmpty(),400,"Transcript applications require at least one supporting file.");
  for(String id:ids) {
   Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM sc_attachment WHERE id=? AND owner_id=? AND (application_id IS NULL OR application_id=?)",Integer.class,id,user.id(),applicationId);
   require(count==1,400,"A file is missing, belongs to another student or is already bound to another application.");
  }
  jdbc.update("UPDATE sc_attachment SET active=false WHERE application_id=?",applicationId);
  for(String id:ids) {
   int changed=jdbc.update("UPDATE sc_attachment SET application_id=?,active=true WHERE id=? AND owner_id=? AND (application_id IS NULL OR application_id=?)",applicationId,id,user.id(),applicationId);
   require(changed==1,409,"A file changed. Refresh and try again.");
  }
 }
 public Models.FileData download(Models.User user,String id) {
  List<Map<String,Object>> rows=jdbc.queryForList("SELECT owner_id,application_id,filename,content_type,file_data FROM sc_attachment WHERE id=?",id);
  require(!rows.isEmpty(),404,"File not found.");Map<String,Object> row=rows.get(0);
  boolean owner=user.id().equals(row.get("owner_id"));
  boolean reviewer="STAFF".equals(user.role()) && row.get("application_id")!=null;
  require(owner || reviewer,404,"File not found.");
  return new Models.FileData((String)row.get("filename"),(String)row.get("content_type"),(byte[])row.get("file_data"));
 }
 public void delete(Models.User user,String id) {
  auth.role(user,"STUDENT");int count=jdbc.update("DELETE FROM sc_attachment WHERE id=? AND owner_id=? AND application_id IS NULL",id,user.id());
  require(count==1,404,"The unsubmitted file was not found or is already attached.");
 }
}
