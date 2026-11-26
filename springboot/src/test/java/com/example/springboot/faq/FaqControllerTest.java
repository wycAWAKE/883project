package com.example.springboot.faq;

import com.example.springboot.faq.controller.FaqController;
import com.example.springboot.faq.entity.FaqEntry;
import com.example.springboot.faq.mapper.FaqMapper;
import com.example.springboot.faq.retrieval.TfIdfRetriever;
import com.example.springboot.faq.service.FaqService;
import com.example.springboot.faq.config.FaqLlmProperties;
import com.example.springboot.faq.llm.LlmClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import com.example.springboot.faq.service.FaqRequestRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FaqControllerTest {
    private final LlmClient llm = new LlmClient(new FaqLlmProperties(false, "", "", "", "max_completion_tokens"),
            new ObjectMapper(), new RestTemplateBuilder());
    private FaqRequestRunner runner;
    @AfterEach
    void closeRunner() { if (runner != null) runner.close(); }

    private FaqMapper mapper;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        // Only the external database boundary is mocked; service and retrieval are real.
        mapper = mock(FaqMapper.class);
        when(mapper.findEnabled()).thenReturn(Collections.singletonList(fixture()));
        mvc = api(0.35);
    }

    @ParameterizedTest
    @ValueSource(strings = {"stu", "dormManager", "admin"})
    void existingRolesCanAskAndReceiveStoredAnswerAndSource(String role) throws Exception {
        mvc.perform(post("/faq/ask").session(session(role))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"PASSWORD reset\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.msg").value("Success"))
                .andExpect(jsonPath("$.data.matched").value(true))
                .andExpect(jsonPath("$.data.faqId").value(7))
                .andExpect(jsonPath("$.data.answer").value("Test fixture answer only."))
                .andExpect(jsonPath("$.data.sourceTitle").value("Test fixture, not campus policy"))
                .andExpect(jsonPath("$.data.sourceUrl").value("https://jwc.ccnu.edu.cn/ywgl/lxwm.htm"))
                .andExpect(jsonPath("$.data.score").value(closeTo(1.0, 1e-12)))
                .andExpect(jsonPath("$.data.guidance").value(nullValue()));
    }

    @Test
    void missingOrIncompleteSessionCannotUseClientSuppliedIdentity() throws Exception {
        mvc.perform(post("/faq/ask").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"password\",\"userId\":1,\"role\":\"admin\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("FAQ_UNAUTHENTICATED"));
        MockHttpSession incomplete = new MockHttpSession();
        incomplete.setAttribute("Identity", "stu");
        mvc.perform(post("/faq/ask").session(incomplete).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"password\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(mapper);
    }

    @Test
    void unknownRoleIsForbidden() throws Exception {
        mvc.perform(post("/faq/ask").session(session("guest")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"password\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FAQ_FORBIDDEN"));
        verifyNoInteractions(mapper);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{}", "{\"question\":null}", "{\"question\":\"  \"}",
            "{\"question\":\"\\u3000\\u00a0\"}", "{broken"})
    void missingBlankOrMalformedQuestionsAreRejected(String body) throws Exception {
        mvc.perform(post("/faq/ask").session(session("stu")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FAQ_INVALID_QUESTION"));
        verifyNoInteractions(mapper);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"question\":\"password reset\"} trailing",
            "{\"question\":\"password reset\"}{\"question\":\"another\"}",
            "{\"question\":123}", "{\"question\":true}"})
    void trailingJsonAndNonStringQuestionsAreRejected(String body) throws Exception {
        mvc.perform(post("/faq/ask").session(session("stu")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FAQ_INVALID_QUESTION"));
        verifyNoInteractions(mapper);
    }

    @Test
    void questionLengthIsCheckedAfterTrimming() throws Exception {
        String thousand = String.join("", Collections.nCopies(1000, "x"));
        mvc.perform(post("/faq/ask").session(session("stu")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\" " + thousand + " \"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/faq/ask").session(session("stu")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"" + thousand + "x\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unrelatedQuestionReturnsGuidanceWithoutLeakingAnAnswer() throws Exception {
        mvc.perform(post("/faq/ask").session(session("stu")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"volcano eruption\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.matched").value(false))
                .andExpect(jsonPath("$.data.answer").value(nullValue()))
                .andExpect(jsonPath("$.data.sourceTitle").value(nullValue()))
                .andExpect(jsonPath("$.data.score").value(0.0))
                .andExpect(jsonPath("$.data.guidance").value("No matching answer was found. Please contact student support."));
    }

    @Test
    void emptyKnowledgeBaseReturnsNormalNoMatch() throws Exception {
        when(mapper.findEnabled()).thenReturn(Collections.emptyList());
        mvc.perform(post("/faq/ask").session(session("stu")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"password reset\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.matched").value(false))
                .andExpect(jsonPath("$.data.faqId").value(nullValue()));
    }

    @Test
    void belowConfiguredThresholdReturnsNoMatchEvenWithSharedTerms() throws Exception {
        api(0.99).perform(post("/faq/ask").session(session("stu")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.matched").value(false))
                .andExpect(jsonPath("$.data.faqId").value(nullValue()));
    }

    @Test
    void exactQuestionStillMatchesAtMaximumThreshold() throws Exception {
        api(1.0).perform(post("/faq/ask").session(session("stu")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"password reset\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.matched").value(true));
    }

    @Test
    void databaseFailureIsUnavailableAndDoesNotExposeSql() throws Exception {
        when(mapper.findEnabled()).thenThrow(new DataAccessResourceFailureException("private SQL credentials"));
        mvc.perform(post("/faq/ask").session(session("stu")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"password\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("FAQ_UNAVAILABLE"))
                .andExpect(jsonPath("$.msg").value("FAQ service is temporarily unavailable. Please try again later."))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void invalidThresholdFailsAtConfigurationTime() {
        for (double value : new double[]{0.0, -0.1, 1.01, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new FaqService(mapper, new TfIdfRetriever(), value, llm));
        }
    }

    private MockMvc api(double threshold) {
        runner = new FaqRequestRunner(new FaqService(mapper, new TfIdfRetriever(), threshold, llm), 35000);
        return MockMvcBuilders.standaloneSetup(new FaqController(runner, new ObjectMapper())).build();
    }

    private MockHttpSession session(String role) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("Identity", role);
        session.setAttribute("User", "server-side test fixture");
        return session;
    }

    private FaqEntry fixture() {
        FaqEntry entry = new FaqEntry();
        entry.setId(7L);
        entry.setQuestion("Password reset");
        entry.setAnswer("Test fixture answer only.");
        entry.setSourceTitle("Test fixture, not campus policy");
        entry.setSourceUrl("https://jwc.ccnu.edu.cn/ywgl/lxwm.htm");
        entry.setEnabled(true);
        return entry;
    }
}
