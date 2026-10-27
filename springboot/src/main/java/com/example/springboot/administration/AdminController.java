package com.example.springboot.administration;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.beans.factory.annotation.Value;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.example.springboot.common.Result;
@RestController("documentApplicationsController")

public class AdminController {
 private static <T> Result<T> success(T data){return new Result<>("0","Success",data);}
 private final Auth auth;private final ApplicationService apps;private final AttachmentService files;private final NotificationService notices;
 public AdminController(Auth auth,ApplicationService apps,AttachmentService files,NotificationService notices){this.auth=auth;this.apps=apps;this.files=files;this.notices=notices;}
 @GetMapping("/applications/session") public Object session(HttpServletRequest req){return success(auth.session(req));}
 @GetMapping("/applications/summary") public Object summary(HttpServletRequest req){return success(apps.summary(auth.current(req)));}
 @GetMapping("/applications") public Object list(@RequestParam(defaultValue="") String status,@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="1") int pageNum,@RequestParam(defaultValue="10") int pageSize,HttpServletRequest req){return success(apps.list(auth.current(req),status,search,pageNum,pageSize));}
 @GetMapping("/applications/{id}") public Object detail(@PathVariable String id,HttpServletRequest req){return success(apps.detail(auth.current(req),id));}
 @PostMapping("/applications") public Object submit(@Valid @RequestBody Api.Submission input,HttpServletRequest req){return success(apps.submit(auth.current(req),input));}
 @PutMapping("/applications/{id}/resubmit") public Object resubmit(@PathVariable String id,@Valid @RequestBody Api.Submission input,HttpServletRequest req){return success(apps.resubmit(auth.current(req),id,input));}
 @PostMapping("/applications/{id}/decision") public Object decide(@PathVariable String id,@Valid @RequestBody Api.Decision input,HttpServletRequest req){return success(apps.decide(auth.current(req),id,input));}
 @GetMapping("/attachments/pending") public Object pending(HttpServletRequest req){return success(files.pending(auth.current(req)));}
 @PostMapping("/attachments") public Object upload(@RequestParam("file") MultipartFile file,HttpServletRequest req) throws IOException {return success(files.upload(auth.current(req),file));}
 @DeleteMapping("/attachments/{id}") public Object deleteFile(@PathVariable String id,HttpServletRequest req){files.delete(auth.current(req),id);return success(null);}
 @GetMapping("/attachments/{id}/download") public ResponseEntity<byte[]> download(@PathVariable String id,HttpServletRequest req){
  Models.FileData file=files.download(auth.current(req),id);
  return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.contentType()))
   .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(file.filename(),StandardCharsets.UTF_8).build().toString())
   .body(file.bytes());
 }
 @GetMapping("/notifications") public Object notifications(@RequestParam(required=false) Boolean unread,@RequestParam(defaultValue="1") int pageNum,@RequestParam(defaultValue="10") int pageSize,HttpServletRequest req){return success(notices.list(auth.current(req),unread,pageNum,pageSize));}
 @GetMapping("/notifications/unread-count") public Object unread(HttpServletRequest req){return success(Collections.singletonMap("count",notices.unread(auth.current(req))));}
 @PutMapping("/notifications/read-all") public Object readAll(HttpServletRequest req){notices.readAll(auth.current(req));return success(null);}
 @PutMapping("/notifications/{id}/read") public Object read(@PathVariable String id,HttpServletRequest req){notices.read(auth.current(req),id);return success(null);}
}
