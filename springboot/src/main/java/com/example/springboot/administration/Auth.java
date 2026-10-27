package com.example.springboot.administration;
import javax.servlet.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import com.example.springboot.entity.Student;
import com.example.springboot.entity.DormManager;
import java.util.*;
import static com.example.springboot.administration.Api.require;
/** Uses the group's existing Session. No second login or password store. */
@Service public class Auth {
 public static final String CSRF="SC_ADMIN_CSRF", BOUND_ID="SC_ADMIN_IDENTITY";
 private final JdbcTemplate jdbc;
 public Auth(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public Models.User current(HttpServletRequest req){
  HttpSession session=req.getSession(false);
  require(session!=null,401,"Please sign in again.");
  Object role=session.getAttribute("Identity"),account=session.getAttribute("User");
  require(role!=null && account!=null,401,"Please sign in again.");
  String username,table,kind;
  if("stu".equals(role) && account instanceof Student){username=((Student)account).getUsername();table="student";kind="STUDENT";}
  else if("dormManager".equals(role) && account instanceof DormManager){username=((DormManager)account).getUsername();table="dorm_manager";kind="STAFF";}
  else throw new Api.Problem(403,"Document services are available to Student and Staff accounts.");
  require(username!=null && username.length()<=50,401,"Invalid account. Please sign in again.");
  List<Map<String,Object>> rows=jdbc.queryForList("SELECT username,name,email FROM "+table+" WHERE username=?",username);
  require(rows.size()==1,401,"This account is no longer available.");
  Map<String,Object> row=rows.get(0);
  String canonical=(String)row.get("username"),id=role+":"+canonical;
  if(!id.equals(session.getAttribute(BOUND_ID))){session.setAttribute(BOUND_ID,id);session.setAttribute(CSRF,UUID.randomUUID().toString());}
  String name=(String)row.get("name"),email=(String)row.get("email");
  return new Models.User(id,canonical,name==null?canonical:name,"STUDENT".equals(kind)?canonical:"","",email==null?"":email,kind);
 }
 public void role(Models.User user,String expected){require(expected.equals(user.role()),403,"You do not have permission to perform this action.");}
 public Map<String,Object> session(HttpServletRequest req){
  Models.User user=current(req);Map<String,Object> result=new LinkedHashMap<>();
  result.put("user",user);result.put("csrfToken",req.getSession(false).getAttribute(CSRF));return result;
 }
}
