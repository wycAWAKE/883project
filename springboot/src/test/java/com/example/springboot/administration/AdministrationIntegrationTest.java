package com.example.springboot.administration;

import com.example.springboot.SpringbootApplication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.http.HttpMethod;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest(classes=SpringbootApplication.class)
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="SC_GROUP_MYSQL_TEST", matches="true")
class AdministrationIntegrationTest {
 @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc;
 MockHttpSession student, other, staff;
 @BeforeEach void setup() throws Exception {
  assertEquals("smartcampus_group_m5m6_test",jdbc.queryForObject("SELECT DATABASE()",String.class),"Tests may delete only their dedicated database records");
  jdbc.update("DELETE FROM sc_notification");jdbc.update("DELETE FROM sc_history");jdbc.update("DELETE FROM sc_attachment");jdbc.update("DELETE FROM sc_application");
  student=login("stu","stu1");other=login("stu","stu2");staff=login("dormManager","dorm1");
 }
 MockHttpSession login(String role,String username)throws Exception {
  Map<String,Object> input=new LinkedHashMap<>();input.put("username",username);input.put("password","123456");
  MvcResult result=mvc.perform(post("/"+role+"/login").contentType("application/json").content(json.writeValueAsBytes(input))).andReturn();
  assertEquals("0",json.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).get("code").asText());
  MockHttpSession session=(MockHttpSession)result.getRequest().getSession(false);
  if(!"admin".equals(role))assertEquals(200,send(session,"GET","/applications/session",null).getResponse().getStatus());
  return session;
 }
 MvcResult send(MockHttpSession session,String method,String path,Object body)throws Exception {
  MockHttpServletRequestBuilder req=request(HttpMethod.valueOf(method),path).session(session);
  Object token=session.getAttribute(Auth.CSRF);if(token!=null)req.header("X-CSRF-Token",token);
  if(body!=null)req.contentType("application/json").content(json.writeValueAsBytes(body));
  return mvc.perform(req).andReturn();
 }
 JsonNode data(MvcResult result)throws Exception {return json.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).get("data");}
 Map<String,Object> submission(List<String> files){
  Map<String,Object> req=new LinkedHashMap<>();req.put("requestKey",UUID.randomUUID().toString());req.put("applicationType","ENROLMENT_CERTIFICATE");req.put("purpose","Evidence for internship application");req.put("documentLanguage","ENGLISH");req.put("deliveryMethod","ELECTRONIC");req.put("notes","Synthetic automated test");req.put("attachmentIds",files);return req;
 }
 JsonNode create()throws Exception {MvcResult r=send(student,"POST","/applications",submission(Collections.emptyList()));assertEquals(200,r.getResponse().getStatus(),r.getResponse().getContentAsString());return data(r);}
 MvcResult decide(String id,String action,String reason,int version)throws Exception {
  Map<String,Object> req=new LinkedHashMap<>();req.put("action",action);req.put("reason",reason);req.put("version",version);return send(staff,"POST","/applications/"+id+"/decision",req);
 }
 String upload(MockHttpSession owner,String name,byte[] bytes,int expected)throws Exception {
  MvcResult r=mvc.perform(multipart("/attachments").file(new MockMultipartFile("file",name,"application/octet-stream",bytes)).session(owner).header("X-CSRF-Token",owner.getAttribute(Auth.CSRF))).andReturn();
  assertEquals(expected,r.getResponse().getStatus(),r.getResponse().getContentAsString());return expected==200?data(r).get("id").asText():null;
 }
 @Test void completeWorkflowWithRealGroupAccounts()throws Exception {
  String file=upload(student,"evidence.pdf","%PDF-1.4 sample".getBytes("UTF-8"),200);
  Map<String,Object> req=submission(Collections.singletonList(file));req.put("applicationType","TRANSCRIPT");
  MvcResult initial=send(student,"POST","/applications",req);assertEquals(200,initial.getResponse().getStatus());String id=data(initial).get("application").get("id").asText();
  assertEquals(200,decide(id,"RETURN","Please explain the purpose more clearly.",0).getResponse().getStatus());
  Map<String,Object> revised=submission(Collections.singletonList(file));revised.put("purpose","Revised purpose for internship documentation");revised.put("version",1);
  assertEquals(200,send(student,"PUT","/applications/"+id+"/resubmit",revised).getResponse().getStatus());
  assertEquals(200,decide(id,"APPROVE","Approved for issue.",2).getResponse().getStatus());
  JsonNode detail=data(send(student,"GET","/applications/"+id,null));assertEquals("APPROVED",detail.get("application").get("status").asText());assertEquals(4,detail.get("history").size());assertEquals(2,detail.get("application").get("revision").asInt());
  assertEquals("stu:stu1",detail.get("application").get("applicantId").asText());assertFalse(detail.get("application").has("requestDigest"));
  assertEquals(4,data(send(student,"GET","/notifications/unread-count",null)).get("count").asInt());assertEquals(200,send(student,"GET","/attachments/"+file+"/download",null).getResponse().getStatus());
 }
 @Test void ownershipAndRoles()throws Exception {
  String id=create().get("application").get("id").asText();assertEquals(404,send(other,"GET","/applications/"+id,null).getResponse().getStatus());
  assertEquals(0,data(send(other,"GET","/applications",null)).get("total").asInt());
  Map<String,Object> d=new LinkedHashMap<>();d.put("action","APPROVE");d.put("version",0);
  assertEquals(403,send(student,"POST","/applications/"+id+"/decision",d).getResponse().getStatus());assertEquals(403,send(staff,"POST","/applications",submission(Collections.emptyList())).getResponse().getStatus());
 }
 @Test void forgedIdentityFieldsDoNotChangeOwnership()throws Exception {
  Map<String,Object> req=submission(Collections.emptyList());req.put("applicantId","stu:stu2");req.put("role","STAFF");req.put("status","APPROVED");
  JsonNode app=data(send(student,"POST","/applications",req)).get("application");assertEquals("stu:stu1",app.get("applicantId").asText());assertEquals("PENDING",app.get("status").asText());
 }
 @Test void anonymousAndCsrfProtection()throws Exception {
  assertEquals(401,mvc.perform(get("/applications")).andReturn().getResponse().getStatus());
  assertEquals(403,mvc.perform(post("/applications").session(student).contentType("application/json").content(json.writeValueAsBytes(submission(Collections.emptyList())))).andReturn().getResponse().getStatus());
  assertEquals(403,mvc.perform(post("/applications").session(student).header("X-CSRF-Token","invalid").contentType("application/json").content("{}")).andReturn().getResponse().getStatus());
 }
 @Test void groupSignOutInvalidatesBusinessAccess()throws Exception {
  assertEquals(200,send(student,"GET","/main/signOut",null).getResponse().getStatus());assertEquals(401,send(student,"GET","/applications",null).getResponse().getStatus());
 }
 @Test void administratorDoesNotBecomeReviewerImplicitly()throws Exception {assertEquals(403,send(login("admin","admin"),"GET","/applications",null).getResponse().getStatus());}
 @Test void canonicalAccountKeySurvivesCaseInsensitiveLogin()throws Exception {
  create();MockHttpSession same=login("stu","STU1");assertEquals(1,data(send(same,"GET","/applications",null)).get("total").asInt());
 }
 @Test void duplicateSubmissionIsIdempotent()throws Exception {
  Map<String,Object> req=submission(Collections.emptyList());MvcResult first=send(student,"POST","/applications",req),second=send(student,"POST","/applications",req);
  assertEquals(data(first).get("application").get("id"),data(second).get("application").get("id"));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM sc_notification",Integer.class));
  req.put("purpose","Changed request under the same key");assertEquals(409,send(student,"POST","/applications",req).getResponse().getStatus());
 }
 @Test void reviewReasonAndTerminalStateRules()throws Exception {
  String id=create().get("application").get("id").asText();assertEquals(400,decide(id,"RETURN"," ",0).getResponse().getStatus());assertEquals(400,decide(id,"REJECT","",0).getResponse().getStatus());assertEquals(200,decide(id,"REJECT","Not eligible for this document.",0).getResponse().getStatus());assertEquals(409,decide(id,"APPROVE","",1).getResponse().getStatus());
 }
 @Test void validationPaginationAndRequiredMaterials()throws Exception {
  Map<String,Object> req=submission(Collections.emptyList());req.put("applicationType","TRANSCRIPT");assertEquals(400,send(student,"POST","/applications",req).getResponse().getStatus());
  req.put("applicationType","OTHER");assertEquals(400,send(student,"POST","/applications",req).getResponse().getStatus());
  assertEquals(400,send(student,"GET","/applications?pageSize=999",null).getResponse().getStatus());assertEquals(400,send(student,"GET","/applications?status=UNKNOWN",null).getResponse().getStatus());assertEquals(400,send(student,"GET","/notifications?pageNum=0",null).getResponse().getStatus());
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM sc_application",Integer.class));
 }
 @Test void missingAttachmentRollsBackApplicationAndHistory()throws Exception {
  assertEquals(400,send(student,"POST","/applications",submission(Collections.singletonList(UUID.randomUUID().toString()))).getResponse().getStatus());
  for(String table:Arrays.asList("sc_application","sc_history","sc_notification"))assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class));
 }
 @Test void failedRevisionRollsBackStatusHistoryAndNotices()throws Exception {
  String id=create().get("application").get("id").asText();decide(id,"RETURN","Missing evidence",0);
  Map<String,Object> req=submission(Collections.singletonList("missing-file"));req.put("version",1);
  assertEquals(400,send(student,"PUT","/applications/"+id+"/resubmit",req).getResponse().getStatus());JsonNode detail=data(send(student,"GET","/applications/"+id,null));assertEquals("RETURNED",detail.get("application").get("status").asText());assertEquals(2,detail.get("history").size());assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM sc_notification",Integer.class));
 }
 @Test void filesAreValidatedAndIsolated()throws Exception {
  String id=upload(student,"private.pdf","%PDF-1.4 private".getBytes("UTF-8"),200);
  assertEquals(404,send(other,"GET","/attachments/"+id+"/download",null).getResponse().getStatus());assertEquals(404,send(staff,"GET","/attachments/"+id+"/download",null).getResponse().getStatus());
  assertEquals(400,send(other,"POST","/applications",submission(Collections.singletonList(id))).getResponse().getStatus());
  upload(student,"fake.pdf","not a pdf".getBytes("UTF-8"),400);upload(student,"huge.pdf",new byte[5*1024*1024+1],413);
  assertEquals(200,send(student,"DELETE","/attachments/"+id,null).getResponse().getStatus());
 }
 @Test void boundFilesKeepPermissionAndHistory()throws Exception {
  String file=upload(student,"evidence.pdf","%PDF-1.4 bound".getBytes("UTF-8"),200);String id=data(send(student,"POST","/applications",submission(Collections.singletonList(file)))).get("application").get("id").asText();
  assertEquals(200,send(staff,"GET","/attachments/"+file+"/download",null).getResponse().getStatus());assertEquals(404,send(student,"DELETE","/attachments/"+file,null).getResponse().getStatus());
  decide(id,"RETURN","Update the purpose",0);Map<String,Object> req=submission(Collections.emptyList());req.put("version",1);send(student,"PUT","/applications/"+id+"/resubmit",req);
  JsonNode detail=data(send(student,"GET","/applications/"+id,null));assertFalse(detail.get("attachments").get(0).get("active").asBoolean());assertTrue(detail.get("history").get(0).get("snapshotJson").asText().contains(file));assertEquals(200,send(student,"GET","/attachments/"+file+"/download",null).getResponse().getStatus());
 }
 @Test void notificationsReadStateBelongsToRecipient()throws Exception {
  create();JsonNode notice=data(send(student,"GET","/notifications",null)).get("records").get(0);String id=notice.get("id").asText();
  assertEquals(404,send(other,"PUT","/notifications/"+id+"/read",null).getResponse().getStatus());assertEquals(0,data(send(other,"GET","/notifications",null)).get("total").asInt());
  assertEquals(200,send(student,"PUT","/notifications/"+id+"/read",null).getResponse().getStatus());assertEquals(200,send(student,"PUT","/notifications/"+id+"/read",null).getResponse().getStatus());assertEquals(0,data(send(student,"GET","/notifications/unread-count",null)).get("count").asInt());assertEquals(0,data(send(student,"GET","/notifications?unread=true",null)).get("total").asInt());
  create();assertEquals(200,send(student,"PUT","/notifications/read-all",null).getResponse().getStatus());assertEquals(0,data(send(student,"GET","/notifications/unread-count",null)).get("count").asInt());
 }
 @Test void competingReviewersCommitOnlyOneDecision()throws Exception {
  String id=create().get("application").get("id").asText();ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
  try {
   List<Future<Integer>> results=new ArrayList<>();for(int i=0;i<2;i++)results.add(pool.submit(()->{start.await();return decide(id,"APPROVE","Concurrent review",0).getResponse().getStatus();}));start.countDown();
   List<Integer> statuses=Arrays.asList(results.get(0).get(20,TimeUnit.SECONDS),results.get(1).get(20,TimeUnit.SECONDS));Collections.sort(statuses);assertEquals(Arrays.asList(200,409),statuses);assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM sc_history",Integer.class));assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM sc_notification",Integer.class));
  }finally{pool.shutdownNow();}
 }
}
