package com.example.springboot.faq;

import com.example.springboot.faq.dto.FaqAnswerResponse;
import com.example.springboot.faq.entity.FaqEntry;
import com.example.springboot.faq.mapper.FaqMapper;
import com.example.springboot.faq.retrieval.TfIdfRetriever;
import com.example.springboot.faq.service.FaqService;
import com.example.springboot.faq.config.FaqLlmProperties;
import com.example.springboot.faq.llm.LlmClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FaqServiceTest {
    private final LlmClient llm = new LlmClient(new FaqLlmProperties(false, "", "", "", "max_completion_tokens"),
            new ObjectMapper(), new RestTemplateBuilder());
    private final FaqMapper mapper = mock(FaqMapper.class);
    private final FaqService service = new FaqService(mapper, new TfIdfRetriever(), 0.35, llm);

    @Test
    void contextsApplyThresholdAndReturnAtMostThreeInStableOrder() {
        when(mapper.findEnabled()).thenReturn(Arrays.asList(entry(9), entry(2), entry(5), entry(1)));
        List<TfIdfRetriever.Match> contexts = service.retrieveContexts("alpha");
        assertEquals(Arrays.asList(1L, 2L, 5L), contexts.stream()
                .map(match -> match.getEntry().getId()).collect(Collectors.toList()));
        assertEquals(1.0, contexts.get(0).getScore(), 1e-12);
        assertEquals(1L, service.ask("alpha").getFaqId());
    }

    @Test
    void lowScoresAreExcludedAndR3NoMatchNormalizesScoreToZero() {
        FaqEntry row = entry(1);
        row.setQuestion("alpha beta");
        when(mapper.findEnabled()).thenReturn(Collections.singletonList(row));
        FaqService strict = new FaqService(mapper, new TfIdfRetriever(), 0.99, llm);
        assertTrue(strict.retrieveContexts("alpha").isEmpty());
        FaqAnswerResponse result = strict.ask("alpha");
        assertFalse(result.isMatched());
        assertNull(result.getAnswer());
        assertEquals(0.0, result.getScore(), 1e-12);
        assertEquals(1, strict.retrieveContexts("alpha beta").size());
    }

    @Test
    void invalidEntriesCannotConsumeCandidateSlotsOrBecomeExtractiveAnswers() {
        List<FaqEntry> rows = new ArrayList<>();
        rows.add(null);
        FaqEntry disabled = entry(1); disabled.setEnabled(false); rows.add(disabled);
        FaqEntry unspecified = entry(2); unspecified.setEnabled(null); rows.add(unspecified);
        FaqEntry noId = entry(3); noId.setId(null); rows.add(noId);
        FaqEntry noAnswer = entry(4); noAnswer.setAnswer("\u3000\u00a0"); rows.add(noAnswer);
        FaqEntry noQuestion = entry(5); noQuestion.setQuestion(null); rows.add(noQuestion);
        FaqEntry noTitle = entry(6); noTitle.setSourceTitle(" "); rows.add(noTitle);
        FaqEntry noSource = entry(7); noSource.setSourceUrl(null); rows.add(noSource);
        FaqEntry noAnswerValue = entry(8); noAnswerValue.setAnswer(null); rows.add(noAnswerValue);
        when(mapper.findEnabled()).thenReturn(rows);
        assertTrue(service.retrieveContexts("alpha").isEmpty());
        assertFalse(service.ask("alpha").isMatched());
        rows.add(entry(10)); rows.add(entry(11)); rows.add(entry(12));
        assertEquals(Arrays.asList(10L, 11L, 12L), service.retrieveContexts("alpha").stream()
                .map(match -> match.getEntry().getId()).collect(Collectors.toList()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "/relative", "javascript:alert(1)", "ftp://jwc.ccnu.edu.cn/file",
            "https://example.com/faq", "https://ccnu.edu.cn.example.com/faq", "https://evilccnu.edu.cn/faq",
            "https://user:secret@jwc.ccnu.edu.cn/faq", "https://jwc.ccnu.edu.cn/bad path", "https:///faq"})
    void invalidOrUnofficialSourceCannotBecomeEvidence(String url) {
        FaqEntry row = entry(1);
        row.setSourceUrl(url);
        when(mapper.findEnabled()).thenReturn(Collections.singletonList(row));
        assertTrue(service.retrieveContexts("alpha").isEmpty());
        assertFalse(service.ask("alpha").isMatched());
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://www.ccnu.edu.cn/", "http://ccnu.edu.cn/faq", "https://JWC.CCNU.EDU.CN/faq"})
    void absoluteOfficialHttpSourcesAreAccepted(String url) {
        FaqEntry row = entry(1); row.setSourceUrl(url);
        when(mapper.findEnabled()).thenReturn(Collections.singletonList(row));
        assertEquals(url, service.retrieveContexts("alpha").get(0).getEntry().getSourceUrl());
    }

    @Test
    void questionAndAnswerBudgetUsesDetachedCopiesAndPreservesFullStoredAnswer() {
        List<FaqEntry> rows = Arrays.asList(entry(1), entry(2), entry(3));
        String original = String.join("", Collections.nCopies(10000, "x"));
        rows.forEach(row -> row.setAnswer(original));
        when(mapper.findEnabled()).thenReturn(rows);
        List<TfIdfRetriever.Match> contexts = service.retrieveContexts("alpha");
        int total = 0;
        for (int i = 0; i < contexts.size(); i++) {
            FaqEntry context = contexts.get(i).getEntry();
            assertNotSame(rows.get(i), context);
            assertEquals(rows.get(i).getId(), context.getId());
            assertEquals(rows.get(i).getSourceTitle(), context.getSourceTitle());
            assertEquals(rows.get(i).getSourceUrl(), context.getSourceUrl());
            assertEquals(1995, context.getAnswer().length());
            total += context.getQuestion().length() + context.getAnswer().length();
        }
        assertEquals(6000, total);
        assertEquals(original, rows.get(0).getAnswer());
        assertEquals(original, service.ask("alpha").getAnswer());
    }

    @Test
    void truncationDoesNotSplitASurrogatePairOrLeaveOnlyWhitespace() {
        FaqEntry row = entry(1);
        row.setAnswer(String.join("", Collections.nCopies(1994, "x")) + "\uD83D\uDE00tail");
        when(mapper.findEnabled()).thenReturn(Collections.singletonList(row));
        String answer = service.retrieveContexts("alpha").get(0).getEntry().getAnswer();
        assertEquals(1994, answer.length());
        assertFalse(Character.isHighSurrogate(answer.charAt(answer.length() - 1)));
        row.setAnswer(String.join("", Collections.nCopies(2000, " ")) + "hidden fact");
        assertTrue(service.retrieveContexts("alpha").isEmpty());
        row.setQuestion(String.join("", Collections.nCopies(2000, "a")));
        assertTrue(service.retrieveContexts("a").isEmpty());
    }

    @Test
    void emptyUnknownAndStopwordQuestionsYieldNoContexts() {
        when(mapper.findEnabled()).thenReturn(Collections.emptyList());
        assertTrue(service.retrieveContexts("alpha").isEmpty());
        when(mapper.findEnabled()).thenReturn(Collections.singletonList(entry(1)));
        assertTrue(service.retrieveContexts("volcano").isEmpty());
        assertTrue(service.retrieveContexts("how can I").isEmpty());
    }

    @Test
    void databaseFailurePropagatesForController503Handling() {
        when(mapper.findEnabled()).thenThrow(new DataAccessResourceFailureException("private database detail"));
        assertThrows(DataAccessResourceFailureException.class, () -> service.retrieveContexts("alpha"));
    }

    private FaqEntry entry(long id) {
        FaqEntry row = new FaqEntry();
        row.setId(id); row.setQuestion("alpha"); row.setAnswer("Test fixture answer.");
        row.setSourceTitle("Test fixture, not campus policy");
        row.setSourceUrl("https://jwc.ccnu.edu.cn/ywgl/lxwm.htm");
        row.setEnabled(true);
        return row;
    }
}
