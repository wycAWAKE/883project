package com.example.springboot.security;
import com.example.springboot.entity.*;
import javax.servlet.http.*;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Shared identity lifecycle; no second authentication mechanism. */
public final class SessionSecurity {
    private SessionSecurity() {}
    public static String owner(HttpSession session) {
        if(session==null) return null;
        Object role=session.getAttribute("Identity"),user=session.getAttribute("User"); String name=null;
        if("stu".equals(role) && user instanceof Student) name=((Student)user).getUsername();
        if("admin".equals(role) && user instanceof Admin) name=((Admin)user).getUsername();
        if("dormManager".equals(role) && user instanceof DormManager) name=((DormManager)user).getUsername();
        return name==null || name.trim().isEmpty()?null:role+":"+name;
    }
    public static String token(HttpSession session) {
        synchronized(session) {
            String owner=owner(session); if(owner==null) throw new IllegalStateException("No authenticated identity");
            if(!owner.equals(session.getAttribute("M2_CSRF_OWNER")) || session.getAttribute("M2_CSRF")==null) {
                session.setAttribute("M2_CSRF_OWNER",owner); session.setAttribute("M2_CSRF",UUID.randomUUID().toString());
            }
            return (String)session.getAttribute("M2_CSRF");
        }
    }
    public static boolean validToken(HttpSession session,String provided) {
        return provided!=null && MessageDigest.isEqual(token(session).getBytes(StandardCharsets.UTF_8),provided.getBytes(StandardCharsets.UTF_8));
    }
    public static void signedIn(HttpServletRequest request,HttpSession session,String role,Object user) {
        request.changeSessionId();
        synchronized(session) {
            session.removeAttribute("M2_CSRF");session.removeAttribute("M2_CSRF_OWNER");
            session.setAttribute("Identity",role);session.setAttribute("User",user);
        }
    }
}
