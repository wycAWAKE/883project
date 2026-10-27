import com.sun.net.httpserver.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
/** Optional local demo host, Java 17+. Normal development still uses Vite. */
public class GroupWeb {
 static final Path ROOT=Path.of("vue/dist").toAbsolutePath().normalize();
 static final HttpClient CLIENT=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).version(HttpClient.Version.HTTP_1_1).build();
 static final Set<String> HOP=Set.of("connection","host","content-length","upgrade","http2-settings","transfer-encoding","expect","keep-alive","proxy-connection");
 public static void main(String[] args)throws Exception{
  if(!Files.isRegularFile(ROOT.resolve("index.html")))throw new IllegalStateException("Build Vue first: npm run build");
  HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",8080),0);
  server.createContext("/",GroupWeb::handle);server.setExecutor(Executors.newFixedThreadPool(12));
  Runtime.getRuntime().addShutdownHook(new Thread(()->server.stop(1)));server.start();
  System.out.println("SmartCampus group project: http://localhost:8080/Login");
 }
 static void handle(HttpExchange ex){
  try{
   String url=ex.getRequestURI().toString();
   if(url.startsWith("/api/")){
    byte[] input=ex.getRequestBody().readNBytes(6*1024*1024+1);
    if(input.length>6*1024*1024){send(ex,413,"application/json","{\"code\":\"413\",\"msg\":\"Request too large.\",\"data\":null}".getBytes());return;}
    HttpRequest.Builder req=HttpRequest.newBuilder(URI.create("http://127.0.0.1:9090"+url.substring(4))).timeout(Duration.ofSeconds(30)).method(ex.getRequestMethod(),input.length==0?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofByteArray(input));
    ex.getRequestHeaders().forEach((key,values)->{if(!HOP.contains(key.toLowerCase(Locale.ROOT)))for(String value:values)req.header(key,value);});
    HttpResponse<byte[]> response=CLIENT.send(req.build(),HttpResponse.BodyHandlers.ofByteArray());
    response.headers().map().forEach((key,values)->{if(!HOP.contains(key.toLowerCase(Locale.ROOT)))ex.getResponseHeaders().put(key,new ArrayList<>(values));});
    send(ex,response.statusCode(),null,response.body());return;
   }
   if(!Set.of("GET","HEAD").contains(ex.getRequestMethod())){send(ex,405,"text/plain","Method not allowed".getBytes());return;}
   String path=ex.getRequestURI().getPath();Path file=ROOT.resolve(path.substring(1)).normalize();
   if(!file.startsWith(ROOT)){send(ex,403,"text/plain","Forbidden".getBytes());return;}
   if(!Files.isRegularFile(file)){
    if(path.startsWith("/assets/") || path.matches(".*\\.[a-zA-Z0-9]+$")){send(ex,404,"text/plain","Not found".getBytes());return;}
    file=ROOT.resolve("index.html");
   }
   String name=file.getFileName().toString();
   String mime=name.endsWith(".js")?"text/javascript; charset=utf-8":name.endsWith(".css")?"text/css; charset=utf-8":name.endsWith(".html")?"text/html; charset=utf-8":Files.probeContentType(file);
   ex.getResponseHeaders().set("X-Content-Type-Options","nosniff");
   if(name.endsWith(".html"))ex.getResponseHeaders().set("Cache-Control","no-store");
   send(ex,200,mime==null?"application/octet-stream":mime,Files.readAllBytes(file));
  }catch(Exception error){try{send(ex,502,"application/json","{\"code\":\"502\",\"msg\":\"Cannot connect to the backend. Check the startup window.\",\"data\":null}".getBytes());}catch(Exception ignored){}}
  finally{ex.close();}
 }
 static void send(HttpExchange ex,int status,String type,byte[] body)throws Exception{
  if(type!=null)ex.getResponseHeaders().set("Content-Type",type);
  ex.sendResponseHeaders(status,"HEAD".equals(ex.getRequestMethod())?-1:body.length);
  if(!"HEAD".equals(ex.getRequestMethod()))ex.getResponseBody().write(body);
 }
}
