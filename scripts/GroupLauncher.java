import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
/** Optional local start helper, Java 17+. Starts only this project's child processes. */
public class GroupLauncher {
 static final Path ROOT=Path.of(".").toAbsolutePath().normalize(), LOCAL=ROOT.resolve(".local");
 static final List<Process> OWNED=new ArrayList<>();
 static String java(){return Path.of(System.getProperty("java.home"),"bin/java.exe").toString();}
 static boolean open(int port){try(Socket socket=new Socket()){socket.connect(new InetSocketAddress("127.0.0.1",port),500);return true;}catch(IOException e){return false;}}
 static Process start(String log,Path cwd,String... command)throws Exception{
  Process p=new ProcessBuilder(command).directory(cwd.toFile()).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(LOCAL.resolve(log).toFile())).start();OWNED.add(p);return p;
 }
 static void waitPort(int port,Process process)throws Exception{
  for(int i=0;i<120;i++){if(!process.isAlive())throw new IOException("A project service stopped. See .local logs.");if(open(port))return;Thread.sleep(500);}
  throw new IOException("Startup timed out on port "+port+". See .local logs.");
 }
 static void stop(){for(int i=OWNED.size()-1;i>=0;i--){Process p=OWNED.get(i);p.destroy();try{if(!p.waitFor(8,TimeUnit.SECONDS))p.destroyForcibly();}catch(InterruptedException e){Thread.currentThread().interrupt();}}}
 public static void main(String[] args){
  try{
   if(Runtime.version().feature()<17)throw new IOException("This optional helper needs Java 17+. Normal Maven/Vite development supports the documented baseline.");
   Files.createDirectories(LOCAL);Properties cfg=new Properties();try(InputStream in=Files.newInputStream(LOCAL.resolve("group-runtime.properties"))){cfg.load(in);}
   if(open(8080)||open(9090))throw new IOException("Port 8080 or 9090 is in use. Check your existing project window before starting again.");
   if(!Files.isRegularFile(LOCAL.resolve("smartcampus-group.jar"))||!Files.isRegularFile(ROOT.resolve("vue/dist/index.html")))throw new IOException("Build the backend JAR and Vue frontend first. See docs/M5_M6_SETUP.md.");
   Runtime.getRuntime().addShutdownHook(new Thread(GroupLauncher::stop));
   int dbPort=Integer.parseInt(cfg.getProperty("port","3307"));
   if(!open(dbPort)){
    Path source=Path.of(cfg.getProperty("mysqlSource",""));
    if(!Files.isRegularFile(source.resolve("scripts/MySqlLauncher.java")))throw new IOException("Start your MySQL server first.");
    Process db=start("database-start.log",source,java(),"scripts/MySqlLauncher.java","--database-only");waitPort(dbPort,db);
   }
   ProcessBuilder backend=new ProcessBuilder(java(),"-jar",LOCAL.resolve("smartcampus-group.jar").toString()).directory(ROOT.toFile()).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(LOCAL.resolve("backend.log").toFile()));
   backend.environment().put("SPRING_DATASOURCE_URL","jdbc:mysql://"+cfg.getProperty("host","127.0.0.1")+":"+dbPort+"/"+cfg.getProperty("database")+"?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai");
   backend.environment().put("SPRING_DATASOURCE_USERNAME",cfg.getProperty("username"));backend.environment().put("SPRING_DATASOURCE_PASSWORD",cfg.getProperty("password"));
   System.out.println("Starting group backend...");Process app=backend.start();OWNED.add(app);waitPort(9090,app);
   Process web=start("web.log",ROOT,java(),"scripts/GroupWeb.java");waitPort(8080,web);
   System.out.println("Ready: http://localhost:8080/Login\nStudent: stu1 / 123456\nStaff: dorm1 / 123456\nKeep this window open. Ctrl+C stops this project.");
   while(app.isAlive()&&web.isAlive())Thread.sleep(1000);
   throw new IOException("A project service stopped. See .local logs.");
  }catch(Exception e){System.err.println("Startup failed: "+e.getMessage());stop();System.exit(1);}
 }
}
