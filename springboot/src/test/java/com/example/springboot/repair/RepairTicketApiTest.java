package com.example.springboot.repair;

import com.example.springboot.controller.RepairTicketController;
import com.example.springboot.entity.Student;
import com.example.springboot.entity.DormManager;
import com.example.springboot.mapper.RepairTicketMapper;
import com.example.springboot.mapper.RepairTicketHistoryMapper;
import com.example.springboot.service.impl.RepairTicketServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RepairTicketApiTest {
    private MockMvc mvc;
    private RepairTicketMapper tickets;
    private RepairTicketHistoryMapper histories;
    private static final String VALID = "{\"title\":\"Light broken\",\"description\":\"Ceiling light is broken\","
            + "\"locationType\":\"CLASSROOM\",\"locationText\":\"Block A room 101\"}";

    @BeforeEach void setup() {
        tickets = mock(RepairTicketMapper.class);
        histories = mock(RepairTicketHistoryMapper.class);
        mvc = MockMvcBuilders.standaloneSetup(new RepairTicketController(
                new RepairTicketServiceImpl(tickets, histories, mock(com.example.springboot.mapper.DormManagerMapper.class)), new M4SessionResolver()))
                .setControllerAdvice(new M4ExceptionHandler()).build();
    }
    @Test void allFourEndpointsRejectAnonymousAccess() throws Exception {
        mvc.perform(post("/repairs").contentType("application/json").content(VALID))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("401"));
        for (String path : new String[]{"/repairs", "/repairs/1", "/repairs/1/history", "/repairs/staff"}) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        }

        for (String path : new String[]{"assignment", "status"}) {
            mvc.perform(put("/repairs/1/"+path).contentType("application/json").content("{\"version\":0}"))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/repairs/1/progress").contentType("application/json").content("{\"version\":0}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(tickets, histories);
    }
    @Test void malformedAndForgedFieldsNeverReachPersistence() throws Exception {
        for (String body : new String[]{"{", "null", "{}", VALID.replace("Light broken", "   "),
                VALID.replace("CLASSROOM", "INVALID"),
                VALID.substring(0, VALID.length()-1)+",\"requesterUsername\":\"someone_else\"}",
                VALID.substring(0, VALID.length()-1)+",\"status\":\"CLOSED\"}"}) {
            mvc.perform(post("/repairs").session(student()).contentType("application/json").content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("400"));
        }
        verifyNoInteractions(tickets, histories);
    }
    @Test void invalidQueryParametersAreClientErrors() throws Exception {
        for (String query : new String[]{"pageNum=0", "pageSize=51", "pageNum=abc", "status=BOGUS", "mine=maybe"}) {
            mvc.perform(get("/repairs?"+query).session(student())).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/repairs/0").session(student())).andExpect(status().isBadRequest());
        mvc.perform(get("/repairs/not-a-number").session(student())).andExpect(status().isBadRequest());
        verifyNoInteractions(tickets, histories);
    }
    @Test void staffCannotCreateStudentTickets() throws Exception {
        DormManager staff = new DormManager(); staff.setUsername("staff1");
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("Identity", "dormManager"); session.setAttribute("User", staff);
        mvc.perform(post("/repairs").session(session).contentType("application/json").content(VALID))
                .andExpect(status().isForbidden());
        verifyNoInteractions(tickets, histories);
    }
    @Test void tooLongTitleAndDescriptionAreRejected() throws Exception {
        String title = new String(new char[101]).replace('\0','x');
        String description = new String(new char[2001]).replace('\0','x');
        for (String body : new String[]{VALID.replace("Light broken",title),
                VALID.replace("Ceiling light is broken",description)}) {
            mvc.perform(post("/repairs").session(student()).contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(tickets, histories);
    }

    @Test void unicodeWhitespaceIsNotAValidRequiredField() throws Exception {
        for (String field : new String[]{"Light broken", "Ceiling light is broken", "Block A room 101"}) {
            mvc.perform(post("/repairs").session(student()).contentType("application/json")
                    .content(VALID.replace(field, "\u3000\u00a0")))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(tickets, histories);
    }
    @Test void writesRejectInvalidVersionRepresentationsAndExtraFields() throws Exception {
        for (String version : new String[]{"null", "-1", "1.5", "\"1\"", "2147483648", "2147483647"}) {
            mvc.perform(post("/repairs/1/progress").session(staff()).contentType("application/json")
                    .content("{\"version\":"+version+",\"note\":\"Working\"}"))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/repairs/1/progress").session(staff()).contentType("application/json")
                .content("{\"version\":0,\"note\":\"Working\",\"actorRole\":\"admin\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(tickets, histories);
    }
    private MockHttpSession staff() {
        DormManager account = new DormManager(); account.setUsername("staff1");
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("Identity", "dormManager"); session.setAttribute("User", account);
        return session;
    }
    private MockHttpSession student() {
        Student account = new Student(); account.setUsername("student1"); account.setName("Same Name");
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("Identity", "stu"); session.setAttribute("User", account);
        return session;
    }
}
