package com.example.springboot.repair;

import com.example.springboot.SpringbootApplication;
import com.example.springboot.entity.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import javax.sql.DataSource;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Properties;
import java.util.UUID;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Opt in with -Dm4.mysql=true. Creates and removes ONLY its own random test schema. */
@SpringBootTest(classes = SpringbootApplication.class)
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named = "m4.mysql", matches = "true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RepairTicketMysqlIntegrationTest {
    private static final String DATABASE = "m4_phase1_test_" + UUID.randomUUID().toString().replace("-", "").substring(0,12);
    private static String serverUrl;
    private static Properties credentials;
    private static boolean created;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    private static final String VALID = "{\"title\":\" Light broken \",\"description\":\"Ceiling light is broken\","
            + "\"locationType\":\"CLASSROOM\",\"locationText\":\"Block A room 101\"}";

    @DynamicPropertySource static void isolateDatabase(DynamicPropertyRegistry registry) throws Exception {
        Properties defaults = PropertiesLoaderUtils.loadProperties(new ClassPathResource("application.properties"));
        String sourceUrl = setting("M4_TEST_MYSQL_URL", "SPRING_DATASOURCE_URL", defaults.getProperty("spring.datasource.url"));
        Matcher match = Pattern.compile("^(jdbc:mysql://[^/]+/)[^?]*(\\?.*)?$").matcher(sourceUrl);
        if (!match.matches()) throw new IllegalStateException("M4 tests require a jdbc:mysql URL.");
        String prefix = match.group(1);
        String query = match.group(2) == null ? "" : match.group(2);
        serverUrl = prefix + query;
        credentials = new Properties();
        credentials.setProperty("user", setting("M4_TEST_MYSQL_USERNAME", "SPRING_DATASOURCE_USERNAME", defaults.getProperty("spring.datasource.username", "")));
        credentials.setProperty("password", setting("M4_TEST_MYSQL_PASSWORD", "SPRING_DATASOURCE_PASSWORD", defaults.getProperty("spring.datasource.password", "")));
        credentials.setProperty("connectTimeout", "3000");
        credentials.setProperty("socketTimeout", "5000");
        try (Connection connection = DriverManager.getConnection(serverUrl, credentials);
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE DATABASE `" + DATABASE + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_bin");
            created = true;
        }
        registry.add("spring.datasource.url", () -> prefix + DATABASE + query);
        registry.add("spring.datasource.username", () -> credentials.getProperty("user"));
        registry.add("spring.datasource.password", () -> credentials.getProperty("password"));
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "3");
    }
    private static String setting(String testName, String appName, String fallback) {
        String value = System.getenv(testName);
        if (value == null) value = System.getenv(appName);
        return value == null ? fallback : value;
    }
    @BeforeEach void prepareSchema() throws Exception {
        Integer present = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='m4_repair_ticket'", Integer.class);
        if (present == 0) {
            try (Connection connection = dataSource.getConnection()) {
                ScriptUtils.executeSqlScript(connection, new FileSystemResource(Paths.get("..", "database", "m4_schema.sql").toAbsolutePath().normalize().toFile()));
            }
        }

        jdbc.execute("CREATE TABLE IF NOT EXISTS dorm_manager (username VARCHAR(64) PRIMARY KEY,"
                + " password VARCHAR(100), dormbuild_id INT, name VARCHAR(100), gender VARCHAR(20),"
                + " age INT, phone_num VARCHAR(30), email VARCHAR(100), avatar VARCHAR(255))"
                + " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci");
        jdbc.update("DELETE FROM dorm_manager");
        jdbc.update("INSERT INTO dorm_manager(username,name,password) VALUES (?,?,?),(?,?,?)",
                "staff1","First Staff","TEST_ONLY", "staff2","Second Staff","TEST_ONLY");
        jdbc.update("DELETE FROM m4_repair_history");
        jdbc.update("DELETE FROM m4_repair_ticket");
    }
    @AfterAll static void cleanupOwnDatabase() throws Exception {
        if (created && DATABASE.matches("m4_phase1_test_[0-9a-f]{12}")) {
            try (Connection connection = DriverManager.getConnection(serverUrl, credentials);
                 Statement statement = connection.createStatement()) {
                statement.executeUpdate("DROP DATABASE `" + DATABASE + "`");
            }
        }
    }
    @Test void createsPersistentTicketAndExactlyOneHistory() throws Exception {
        long id = create("StudentA");
        mvc.perform(get("/repairs/"+id).session(student("StudentA")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.title").value("Light broken"))
                .andExpect(jsonPath("$.data.requesterUsername").value("StudentA"))
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.version").value(0))
                .andExpect(jsonPath("$.data.createdAt").value(org.hamcrest.Matchers.matchesPattern("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")))
                .andExpect(jsonPath("$.data.password").doesNotExist());
        mvc.perform(get("/repairs/"+id+"/history").session(student("StudentA")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].action").value("CREATED"))
                .andExpect(jsonPath("$.data[0].actorRole").value("stu"));
        assertEquals(1, count("m4_repair_ticket")); assertEquals(1, count("m4_repair_history"));
    }
    @Test void ownershipDoesNotDependOnDisplayNameOrMineFlagOrCase() throws Exception {
        long id = create("StudentA");
        for (String other : new String[]{"StudentB", "studenta"}) {
            mvc.perform(get("/repairs?mine=false").session(student(other)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
            mvc.perform(get("/repairs/"+id).session(student(other))).andExpect(status().isNotFound());
            mvc.perform(get("/repairs/"+id+"/history").session(student(other))).andExpect(status().isNotFound());
        }
    }
    @Test void staffAndAdminCanReadButCannotSubmit() throws Exception {
        long id = create("StudentA");
        for (String role : new String[]{"dormManager", "admin"}) {
            MockHttpSession session = manager(role);
            mvc.perform(get("/repairs/"+id).session(session)).andExpect(status().isOk());
            mvc.perform(get("/repairs").session(session)).andExpect(jsonPath("$.data.total").value(1));
            mvc.perform(post("/repairs").session(session).contentType("application/json").content(VALID))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get("/repairs?mine=true").session(manager("dormManager")))
                .andExpect(jsonPath("$.data.total").value(0));
    }
    @Test void historyFailureRollsBackTheActualMysqlTicketInsert() throws Exception {
        // Inject an actual database failure in this isolated schema, not a mock failure.
        jdbc.execute("ALTER TABLE m4_repair_history ADD CONSTRAINT chk_m4_test_reject CHECK (action <> 'CREATED')");
        try {
            mvc.perform(post("/repairs").session(student("StudentA")).contentType("application/json").content(VALID))
                    .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("500"));
        } finally {
            jdbc.execute("ALTER TABLE m4_repair_history DROP CHECK chk_m4_test_reject");
        }
        assertEquals(0, count("m4_repair_ticket")); assertEquals(0, count("m4_repair_history"));
    }
    @Test void paginationAndFiltersUseRealMysql() throws Exception {
        create("StudentA"); create("StudentA"); long newest = create("StudentA"); create("StudentB");
        mvc.perform(get("/repairs?pageSize=2&status=SUBMITTED&keyword=Light").session(student("StudentA")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.records.length()").value(2))
                .andExpect(jsonPath("$.data.records[0].id").value(newest));
        mvc.perform(get("/repairs?pageNum=2&pageSize=2").session(student("StudentA")))
                .andExpect(jsonPath("$.data.records.length()").value(1));
        mvc.perform(get("/repairs?status=CLOSED").session(student("StudentA")))
                .andExpect(jsonPath("$.data.total").value(0));
    }
    @Test void forgedFieldsAndInvalidInputsDoNotWriteRecords() throws Exception {
        for (String body : new String[]{"{}", VALID.replace("CLASSROOM","UNKNOWN"),
                VALID.substring(0,VALID.length()-1)+",\"requesterUsername\":\"StudentB\"}"}) {
            mvc.perform(post("/repairs").session(student("StudentA")).contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
        assertEquals(0, count("m4_repair_ticket")); assertEquals(0, count("m4_repair_history"));
    }

    @Test void fullWorkflowProducesStableHistoryAndCompletionTimes() throws Exception {
        long id = create("StudentA");
        assign(id,"staff1",0,manager("dormManager"),200);
        change(id,"IN_PROGRESS",1,"",manager("dormManager"),200);
        mvc.perform(post("/repairs/"+id+"/progress").session(manager("dormManager"))
                .contentType("application/json").content("{\"version\":2,\"note\":\"Replacing light\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(3));
        change(id,"RESOLVED",3,"Light replaced",manager("dormManager"),200);
        change(id,"CLOSED",4,"Checked and closed",manager("dormManager"),200);
        mvc.perform(get("/repairs/"+id).session(student("StudentA")))
                .andExpect(jsonPath("$.data.status").value("CLOSED"))
                .andExpect(jsonPath("$.data.version").value(5))
                .andExpect(jsonPath("$.data.resolvedAt").isString())
                .andExpect(jsonPath("$.data.closedAt").isString());
        mvc.perform(get("/repairs/"+id+"/history").session(student("StudentA")))
                .andExpect(jsonPath("$.data.length()").value(6))
                .andExpect(jsonPath("$.data[1].action").value("ASSIGNED"))
                .andExpect(jsonPath("$.data[2].action").value("STARTED"))
                .andExpect(jsonPath("$.data[3].action").value("PROGRESS_ADDED"))
                .andExpect(jsonPath("$.data[4].action").value("RESOLVED"))
                .andExpect(jsonPath("$.data[5].action").value("CLOSED"));
        change(id,"CLOSED",5,"Again",manager("dormManager"),409);
        assertEquals(6,count("m4_repair_history"));
    }
    @Test void staffOptionsAreProtectedAndContainOnlySafeFields() throws Exception {
        mvc.perform(get("/repairs/staff")).andExpect(status().isUnauthorized());
        mvc.perform(get("/repairs/staff").session(student("StudentA"))).andExpect(status().isForbidden());
        String response = mvc.perform(get("/repairs/staff").session(manager("admin")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].username").value("staff1"))
                .andExpect(jsonPath("$.data[0].password").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertEquals(2,json.readTree(response).path("data").get(0).size());
    }
    @Test void reassignmentUsesCanonicalUsernameAndRevokesPreviousOwner() throws Exception {
        long id = create("StudentA");
        assign(id,"STAFF1",0,manager("dormManager"),200);
        mvc.perform(get("/repairs/"+id).session(manager("admin")))
                .andExpect(jsonPath("$.data.assigneeUsername").value("staff1"));
        assign(id,"staff2",1,manager("dormManager"),200);
        change(id,"IN_PROGRESS",2,"Start",manager("dormManager"),403);
        change(id,"IN_PROGRESS",2,"Start",staff2(),200);
        mvc.perform(get("/repairs/"+id+"/history").session(student("StudentA")))
                .andExpect(jsonPath("$.data[2].action").value("REASSIGNED"))
                .andExpect(jsonPath("$.data[2].note").value(org.hamcrest.Matchers.containsString("staff2")));
        mvc.perform(get("/repairs?mine=true").session(manager("dormManager")))
                .andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get("/repairs?mine=true").session(staff2()))
                .andExpect(jsonPath("$.data.total").value(1));
    }
    @Test void invalidAssignmentStateAndVersionsNeverAddHistory() throws Exception {
        long id = create("StudentA");
        assign(id,"staff1",0,student("StudentA"),403);
        assign(id,"missing",0,manager("admin"),400);
        assign(id,"staff1",1,manager("admin"),409);
        assign(id,"staff1",0,manager("admin"),200);
        assign(id,"staff1",1,manager("admin"),409);
        change(id,"RESOLVED",1,"Skip",manager("admin"),409);
        change(id,"IN_PROGRESS",0,"Stale",manager("admin"),409);
        change(id,"IN_PROGRESS",1,"Start",manager("admin"),200);
        assign(id,"staff2",2,manager("admin"),409);
        change(id,"RESOLVED",2,"\u3000\u00a0",manager("admin"),400);
        assertEquals(3,count("m4_repair_history"));
        assertEquals("IN_PROGRESS",jdbc.queryForObject("SELECT status FROM m4_repair_ticket WHERE id=?",String.class,id));
    }
    @Test void nonOwnerAndProgressOutsideProcessingAreRejected() throws Exception {
        long id = create("StudentA");
        assign(id,"staff1",0,manager("admin"),200);
        change(id,"IN_PROGRESS",1,"Start",staff2(),403);
        mvc.perform(post("/repairs/"+id+"/progress").session(manager("dormManager"))
                .contentType("application/json").content("{\"version\":1,\"note\":\"Too early\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/repairs/"+id+"/progress").session(student("StudentA"))
                .contentType("application/json").content("{\"version\":1,\"note\":\"Forged\"}"))
                .andExpect(status().isForbidden());
        assertEquals(2,count("m4_repair_history"));
    }
    @Test void historyFailureRollsBackAnAssignmentUpdateAndItsVersion() throws Exception {
        long id = create("StudentA");
        jdbc.execute("ALTER TABLE m4_repair_history ADD CONSTRAINT chk_m4_test_reject_update CHECK (action='CREATED')");
        try { assign(id,"staff1",0,manager("admin"),500); }
        finally { jdbc.execute("ALTER TABLE m4_repair_history DROP CHECK chk_m4_test_reject_update"); }
        mvc.perform(get("/repairs/"+id).session(manager("admin")))
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.version").value(0))
                .andExpect(jsonPath("$.data.assigneeUsername").isEmpty());
        assertEquals(1,count("m4_repair_history"));
    }
    @Test void concurrentAssignmentsWithOneVersionHaveExactlyOneWinner() throws Exception {
        long id = create("StudentA");
        race(() -> assignmentStatus(id,"staff1",0), () -> assignmentStatus(id,"staff2",0));
        assertEquals(1,jdbc.queryForObject("SELECT version FROM m4_repair_ticket WHERE id=?",Integer.class,id));
        assertEquals(2,count("m4_repair_history"));
    }
    @Test void concurrentProgressNotesHaveExactlyOneWinner() throws Exception {
        long id = create("StudentA");
        assign(id,"staff1",0,manager("admin"),200);
        change(id,"IN_PROGRESS",1,"Start",manager("admin"),200);
        Callable<Integer> request = () -> mvc.perform(post("/repairs/"+id+"/progress").session(manager("dormManager"))
                .contentType("application/json").content("{\"version\":2,\"note\":\"Progress update\"}"))
                .andReturn().getResponse().getStatus();
        race(request,request);
        assertEquals(3,jdbc.queryForObject("SELECT version FROM m4_repair_ticket WHERE id=?",Integer.class,id));
        assertEquals(4,count("m4_repair_history"));
    }
    private void race(Callable<Integer> first, Callable<Integer> second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        try {
            List<Future<Integer>> results = new java.util.ArrayList<>();
            for (Callable<Integer> request : Arrays.asList(first,second)) results.add(executor.submit(() -> {
                ready.countDown();
                if (!start.await(5,TimeUnit.SECONDS)) throw new IllegalStateException("Race did not start.");
                return request.call();
            }));
            assertTrue(ready.await(5,TimeUnit.SECONDS)); start.countDown();
            List<Integer> statuses = Arrays.asList(results.get(0).get(10,TimeUnit.SECONDS),results.get(1).get(10,TimeUnit.SECONDS));
            assertTrue(statuses.contains(200), statuses.toString());
            assertTrue(statuses.contains(409), statuses.toString());
        } finally { start.countDown(); executor.shutdownNow(); }
    }
    private int assignmentStatus(long id,String username,int version) throws Exception {
        return mvc.perform(put("/repairs/"+id+"/assignment").session(manager("admin")).contentType("application/json")
                .content(json.writeValueAsString(new java.util.LinkedHashMap<String,Object>() {{
                    put("assigneeUsername",username); put("version",version);
                }}))).andReturn().getResponse().getStatus();
    }
    private void assign(long id,String username,int version,MockHttpSession session,int expected) throws Exception {
        java.util.Map<String,Object> body = new java.util.LinkedHashMap<>();
        body.put("assigneeUsername",username); body.put("version",version);
        mvc.perform(put("/repairs/"+id+"/assignment").session(session).contentType("application/json")
                .content(json.writeValueAsString(body))).andExpect(status().is(expected));
    }
    private void change(long id,String target,int version,String note,MockHttpSession session,int expected) throws Exception {
        java.util.Map<String,Object> body = new java.util.LinkedHashMap<>();
        body.put("targetStatus",target); body.put("version",version); body.put("note",note);
        mvc.perform(put("/repairs/"+id+"/status").session(session).contentType("application/json")
                .content(json.writeValueAsString(body))).andExpect(status().is(expected));
    }
    private MockHttpSession staff2() {
        DormManager account = new DormManager(); account.setUsername("staff2"); return session("dormManager",account);
    }
    private int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM "+table, Integer.class); }
    private long create(String username) throws Exception {
        String response = mvc.perform(post("/repairs").session(student(username)).contentType("application/json").content(VALID))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).path("data").path("id").asLong();
    }
    private MockHttpSession student(String username) {
        Student account = new Student(); account.setUsername(username); account.setName("Same Display Name");
        return session("stu", account);
    }
    private MockHttpSession manager(String role) {
        if ("admin".equals(role)) {
            Admin account = new Admin(); account.setUsername("admin"); return session(role,account);
        }
        DormManager account = new DormManager(); account.setUsername("staff1"); return session(role,account);
    }
    private MockHttpSession session(String role, Object account) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("Identity",role); session.setAttribute("User",account); return session;
    }
}
