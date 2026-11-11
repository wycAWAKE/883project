package com.example.springboot.repair;

import com.example.springboot.SpringbootApplication;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import javax.sql.DataSource;
import java.io.File;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

/** Optional actual login + Vite + browser check, isolated from the business database. */
@SpringBootTest(classes=SpringbootApplication.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfSystemProperty(named="m4.browser",matches="true")
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class RepairStudentBrowserIntegrationTest {
    private static final String DATABASE="m4_browser_test_"+UUID.randomUUID().toString().replace("-", "").substring(0,12);
    private static String serverUrl;
    private static Properties credentials;
    private static boolean created;
    @LocalServerPort int port;
    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;
    @Autowired org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext originalServer;

    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) throws Exception {
        Properties defaults=PropertiesLoaderUtils.loadProperties(new ClassPathResource("application.properties"));
        String source=setting("M4_TEST_MYSQL_URL","SPRING_DATASOURCE_URL",defaults.getProperty("spring.datasource.url"));
        Matcher match=Pattern.compile("^(jdbc:mysql://[^/]+/)[^?]*(\\?.*)?$").matcher(source);
        if (!match.matches()) throw new IllegalStateException("A jdbc:mysql connection is required.");
        String prefix=match.group(1),query=match.group(2)==null ? "" : match.group(2);
        serverUrl=prefix+query; credentials=new Properties();
        credentials.setProperty("user",setting("M4_TEST_MYSQL_USERNAME","SPRING_DATASOURCE_USERNAME",defaults.getProperty("spring.datasource.username","")));
        credentials.setProperty("password",setting("M4_TEST_MYSQL_PASSWORD","SPRING_DATASOURCE_PASSWORD",defaults.getProperty("spring.datasource.password","")));
        credentials.setProperty("connectTimeout","3000"); credentials.setProperty("socketTimeout","5000");
        try(Connection connection=DriverManager.getConnection(serverUrl,credentials); Statement statement=connection.createStatement()) {
            statement.executeUpdate("CREATE DATABASE `"+DATABASE+"` CHARACTER SET utf8mb4 COLLATE utf8mb4_bin"); created=true;
        }
        registry.add("spring.datasource.url",()->prefix+DATABASE+query);
        registry.add("spring.datasource.username",()->credentials.getProperty("user"));
        registry.add("spring.datasource.password",()->credentials.getProperty("password"));
    }
    private static String setting(String first,String second,String fallback) {
        String value=System.getenv(first); if(value==null) value=System.getenv(second); return value==null?fallback:value;
    }
    @AfterAll static void cleanup() throws Exception {
        if(created && DATABASE.matches("m4_browser_test_[0-9a-f]{12}")) {
            try(Connection connection=DriverManager.getConnection(serverUrl,credentials); Statement statement=connection.createStatement()) {
                statement.executeUpdate("DROP DATABASE `"+DATABASE+"`");
            }
        }
    }
    @Test void studentWorkflowInRealBrowser() throws Exception {
        Path project=Paths.get("..").toAbsolutePath().normalize(),vue=project.resolve("vue");
        try(Connection connection=dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection,new FileSystemResource(project.resolve("database/m4_schema.sql").toFile()));
        }
        jdbc.execute("CREATE TABLE student(username VARCHAR(50) PRIMARY KEY,password VARCHAR(100),name VARCHAR(50),age INT,gender VARCHAR(10),phone_num VARCHAR(30),email VARCHAR(100),avatar VARCHAR(255))");
        jdbc.execute("CREATE TABLE dorm_manager(username VARCHAR(50) PRIMARY KEY,password VARCHAR(100),dormbuild_id INT,name VARCHAR(50),gender VARCHAR(10),age INT,phone_num VARCHAR(30),email VARCHAR(100),avatar VARCHAR(255))");
        jdbc.update("INSERT INTO student(username,password,name) VALUES (?,?,?),(?,?,?)",
                "m4_student_a","m4_demo_only","M4 Student A","m4_student_b","m4_demo_only","M4 Student B");
        jdbc.update("INSERT INTO dorm_manager(username,password,name,dormbuild_id) VALUES (?,?,?,?)","m4_staff","m4_demo_only","M4 Staff",1);
        jdbc.update("INSERT INTO dorm_manager(username,password,name,dormbuild_id) VALUES (?,?,?,?)","m4_staff_other","m4_demo_only","M4 Other Staff",1);
        jdbc.execute("CREATE TABLE admin LIKE student");
        jdbc.update("INSERT INTO admin(username,password,name) VALUES (?,?,?)","m4_admin","m4_demo_only","M4 Administrator");
        int vitePort; try(ServerSocket socket=new ServerSocket(0)) { vitePort=socket.getLocalPort(); }
        String origin="http://127.0.0.1:"+vitePort;
        String node=System.getenv("M4_BROWSER_NODE")==null?"node":System.getenv("M4_BROWSER_NODE");
        Path output=Paths.get("target/m4-browser").toAbsolutePath(); Files.createDirectories(output);
        ProcessBuilder vite=new ProcessBuilder(node,vue.resolve("node_modules/vite/bin/vite.js").toString(),"--host","127.0.0.1","--port",String.valueOf(vitePort),"--strictPort");
        vite.directory(vue.toFile()).redirectErrorStream(true).redirectOutput(output.resolve("vite.log").toFile());
        vite.environment().put("SMARTCAMPUS_API_TARGET","http://127.0.0.1:"+port);
        removeDatabaseSecrets(vite);
        Process server=vite.start(),browser=null;
        try {
            boolean ready=false;
            for(int attempt=0;attempt<100;attempt++) {
                if(!server.isAlive()) break;
                HttpURLConnection connection=null;
                try {
                    connection=(HttpURLConnection)new URL(origin).openConnection();
                    connection.setConnectTimeout(200); connection.setReadTimeout(500);
                    if(connection.getResponseCode()==200) { ready=true; break; }
                } catch(Exception ignored) { } finally { if(connection!=null) connection.disconnect(); }
                Thread.sleep(200);
            }
            assertTrue(ready,"Vite did not become ready; see target/m4-browser/vite.log.");
            ProcessBuilder runner=new ProcessBuilder(node,vue.resolve("tests/e2e/repair-student.browser.mjs").toString());
            runner.directory(vue.toFile()).redirectErrorStream(true).redirectOutput(output.resolve("browser.log").toFile());
            runner.environment().put("M4_BROWSER_BASE_URL",origin); runner.environment().put("M4_BROWSER_OUTPUT",output.toString());
            removeDatabaseSecrets(runner); browser=runner.start();
            assertTrue(browser.waitFor(120,TimeUnit.SECONDS),"Browser test timed out.");
            assertEquals(0,browser.exitValue(),"Browser check failed; inspect target/m4-browser/browser.log and screenshots.");
            assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM m4_repair_ticket",Integer.class));
            assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM m4_repair_ticket WHERE status='CLOSED'",Integer.class));
            assertEquals(14,jdbc.queryForObject("SELECT COUNT(*) FROM m4_repair_history",Integer.class));
            verifyIndependentServerRestart();
        } finally {
            if(browser!=null && browser.isAlive()) browser.destroyForcibly();
            server.destroy(); if(!server.waitFor(5,TimeUnit.SECONDS)) server.destroyForcibly();
        }
    }

    private void verifyIndependentServerRestart() throws Exception {
        String url;
        try(Connection connection=dataSource.getConnection()) { url=connection.getMetaData().getURL(); }
        long ticketId=jdbc.queryForObject("SELECT id FROM m4_repair_ticket WHERE title='Staff workflow report'",Long.class);
        originalServer.getWebServer().stop();
        java.util.Map<String,Object> properties=new java.util.HashMap<>();
        properties.put("server.port","0"); properties.put("spring.datasource.url",url);
        properties.put("spring.datasource.username",credentials.getProperty("user"));
        properties.put("spring.datasource.password",credentials.getProperty("password"));
        properties.put("spring.main.banner-mode","off");
        org.springframework.core.env.StandardEnvironment environment=new org.springframework.core.env.StandardEnvironment();
        environment.getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("m4-isolated-restart",properties));
        try(org.springframework.context.ConfigurableApplicationContext restarted=new org.springframework.boot.builder.SpringApplicationBuilder(SpringbootApplication.class).environment(environment).run()) {
            DataSource reopened=restarted.getBean(DataSource.class);
            try(Connection connection=reopened.getConnection()) { assertEquals(DATABASE,connection.getCatalog()); }
            int restartedPort=((org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext)restarted).getWebServer().getPort();
            org.springframework.boot.test.web.client.TestRestTemplate client=new org.springframework.boot.test.web.client.TestRestTemplate();
            String origin="http://127.0.0.1:"+restartedPort;
            java.util.Map<String,String> account=new java.util.HashMap<>(); account.put("username","m4_student_a"); account.put("password","m4_demo_only");
            org.springframework.http.ResponseEntity<java.util.Map> login=client.postForEntity(origin+"/stu/login",account,java.util.Map.class);
            assertEquals("0",login.getBody().get("code"));
            org.springframework.http.HttpHeaders headers=new org.springframework.http.HttpHeaders();
            headers.add("Cookie",login.getHeaders().getFirst("Set-Cookie").split(";",2)[0]);
            org.springframework.http.HttpEntity<Void> request=new org.springframework.http.HttpEntity<>(headers);
            java.util.Map detail=client.exchange(origin+"/repairs/"+ticketId,org.springframework.http.HttpMethod.GET,request,java.util.Map.class).getBody();
            assertEquals("CLOSED",((java.util.Map)detail.get("data")).get("status"));
            java.util.Map history=client.exchange(origin+"/repairs/"+ticketId+"/history",org.springframework.http.HttpMethod.GET,request,java.util.Map.class).getBody();
            assertEquals(8,((java.util.List)history.get("data")).size());
        }
    }

    private void removeDatabaseSecrets(ProcessBuilder process) {
        process.environment().remove("M4_TEST_MYSQL_PASSWORD"); process.environment().remove("SPRING_DATASOURCE_PASSWORD");
    }
}
