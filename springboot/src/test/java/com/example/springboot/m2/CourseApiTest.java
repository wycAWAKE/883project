package com.example.springboot.m2;

import com.example.springboot.entity.Student;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;
import java.util.*;

class CourseApiTest {
    MockMvc mvc;
    CourseService service;

    MockHttpSession student(String name) {
        MockHttpSession session = new MockHttpSession();
        Student s = new Student();
        s.setUsername(name);
        session.setAttribute("Identity", "stu");
        session.setAttribute("User", s);
        return session;
    }

    @BeforeEach
    void setup() {
        service = mock(CourseService.class);
        mvc =
                MockMvcBuilders.standaloneSetup(new CourseController(service, new ObjectMapper()))
                        .addInterceptors(new CourseSessionInterceptor())
                        .setControllerAdvice(new CourseAdvice())
                        .build();
    }

    @Test
    void requiresStudentSession() throws Exception {
        mvc.perform(
                        post("/courses/recommendations/assisted")
                                .contentType("application/json")
                                .content("{\"interests\":\"python\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/courses/catalog")).andExpect(status().isUnauthorized());
        MockHttpSession s = student("admin");
        s.setAttribute("Identity", "admin");
        mvc.perform(get("/courses/history").session(s)).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void assistedEndpointUsesSameServerIdentityValidationAndCsrf() throws Exception {
        MockHttpSession s = student("student1");
        mvc.perform(get("/courses/me").session(s));
        mvc.perform(
                        post("/courses/recommendations/assisted")
                                .session(s)
                                .contentType("application/json")
                                .content("{\"interests\":\"python\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(
                        post("/courses/recommendations/assisted")
                                .session(s)
                                .header("X-M2-CSRF", s.getAttribute("M2_CSRF"))
                                .contentType("application/json")
                                .content("{\"interests\":\"python\",\"completed\":[\"A\"]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        post("/courses/recommendations/assisted")
                                .session(s)
                                .header("X-M2-CSRF", s.getAttribute("M2_CSRF"))
                                .contentType("application/json")
                                .content("{\"interests\":\"python\"}"))
                .andExpect(status().isOk());
        verify(service).assisted("student1", "python", "", 3);
    }

    @Test
    void identityIsServerSideAndCsrfIsSessionBound() throws Exception {
        MockHttpSession a = student("student1"), b = student("student2");
        mvc.perform(get("/courses/me").session(a))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
        String token = (String) a.getAttribute("M2_CSRF");
        mvc.perform(
                        post("/courses/recommendations")
                                .session(a)
                                .contentType("application/json")
                                .content("{\"interests\":\"python\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(
                        post("/courses/recommendations")
                                .session(b)
                                .header("X-M2-CSRF", token)
                                .contentType("application/json")
                                .content("{\"interests\":\"python\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(
                        post("/courses/recommendations")
                                .session(a)
                                .header("X-M2-CSRF", token)
                                .contentType("application/json")
                                .content(
                                        "{\"interests\":\"python\",\"goals\":\"programming\",\"topK\":2}"))
                .andExpect(status().isOk());
        verify(service).recommend("student1", "python", "programming", 2);
    }

    @Test
    void rejectsForgedCompletionsUnknownFieldsInvalidBoundsAndTypes() throws Exception {
        MockHttpSession s = student("student1");
        mvc.perform(get("/courses/me").session(s));
        for (String body :
                Arrays.asList(
                        "{\"interests\":\"python\",\"completed\":[\"6.006\"]}",
                        "{\"interests\":\"python\",\"studentId\":\"student2\"}",
                        "{\"interests\":\"python\",\"topK\":4}",
                        "{\"interests\":\"python\",\"topK\":true}",
                        "{\"interests\":\"  \"}",
                        "{\"interests\":[]}",
                        "[]"))
            mvc.perform(
                            post("/courses/recommendations")
                                    .session(s)
                                    .header("X-M2-CSRF", s.getAttribute("M2_CSRF"))
                                    .contentType("application/json")
                                    .content(body))
                    .andExpect(status().isBadRequest());
        verify(service, never()).recommend(anyString(), anyString(), anyString(), anyInt());
    }

    @Test
    void historyAlwaysUsesLoggedInUsernameAndNoStore() throws Exception {
        mvc.perform(get("/courses/history?studentId=student2").session(student("student1")))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"));
        verify(service).history("student1");
    }

    @Test
    void changingStudentInSameSessionCannotReusePreviousCsrf() throws Exception {
        MockHttpSession s = student("student1");
        mvc.perform(get("/courses/me").session(s));
        String old = (String) s.getAttribute("M2_CSRF");
        Student b = new Student();
        b.setUsername("student2");
        s.setAttribute("User", b);
        mvc.perform(
                        post("/courses/recommendations")
                                .session(s)
                                .header("X-M2-CSRF", old)
                                .contentType("application/json")
                                .content("{\"interests\":\"python\"}"))
                .andExpect(status().isForbidden());
    }
}
