package com.example.springboot.administration;
import javax.servlet.http.*;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.*;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.http.MediaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.springboot.common.Result;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
@Configuration @MapperScan(value="com.example.springboot.administration.mapper",nameGenerator=org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator.class)
public class WebConfig implements WebMvcConfigurer {
 private final Auth auth;private final ObjectMapper json;
 public WebConfig(Auth auth,ObjectMapper json){this.auth=auth;this.json=json;}
 @Override public void addInterceptors(InterceptorRegistry registry){
  registry.addInterceptor(new HandlerInterceptor(){
   @Override public boolean preHandle(HttpServletRequest req,HttpServletResponse res,Object handler)throws Exception{
    res.setHeader("X-Content-Type-Options","nosniff");res.setHeader("Cache-Control","no-store");
    try{
     auth.current(req);
     if(!"GET".equals(req.getMethod()) && !"HEAD".equals(req.getMethod()) && !"OPTIONS".equals(req.getMethod())){
      String token=(String)req.getSession(false).getAttribute(Auth.CSRF),supplied=req.getHeader("X-CSRF-Token");
      Api.require(token!=null && supplied!=null && MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8)),403,"Your form session expired. Refresh and try again.");
     }
     return true;
    }catch(Api.Problem e){
     res.setStatus(e.status);res.setContentType(MediaType.APPLICATION_JSON_VALUE);res.setCharacterEncoding("UTF-8");
     json.writeValue(res.getWriter(),Result.error(String.valueOf(e.status),e.getMessage()));return false;
    }
   }
  }).addPathPatterns("/applications","/applications/**","/attachments","/attachments/**","/notifications","/notifications/**");
 }
}
