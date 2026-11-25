package com.example.springboot.faq;

import com.example.springboot.faq.entity.FaqEntry;
import com.example.springboot.faq.retrieval.TfIdfRetriever;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class TfIdfRetrieverTest {
    private final TfIdfRetriever retriever = new TfIdfRetriever();

    @Test
    void exactQuestionMatchesAfterCaseAndPunctuationNormalization() {
        TfIdfRetriever.Match match = retriever.findBest("HOW can I reset my PASSWORD?",
                Arrays.asList(entry(1, "How can I reset my password?"), entry(2, "Library opening hours")));
        assertEquals(1L, match.getEntry().getId());
        assertEquals(1.0, match.getScore(), 1e-12);
    }

    @Test
    void scoreUsesCosineNormalizationInsteadOfSummingTermWeights() {
        TfIdfRetriever.Match match = retriever.findBest("alpha",
                Collections.singletonList(entry(1, "alpha beta")));
        // Both words have IDF=1. Vector (1,0) against (1,1) has cosine 1/sqrt(2).
        assertEquals(0.7071067811865475, match.getScore(), 1e-12);
    }

    @Test
    void keywordsParticipateInRetrieval() {
        FaqEntry faq = entry(1, "Account access");
        faq.setKeywords("password reset");
        assertTrue(retriever.findBest("password", Collections.singletonList(faq)).getScore() > 0);
    }

    @Test
    void equalScoresPreferSmallerIdRegardlessOfInputOrder() {
        TfIdfRetriever.Match match = retriever.findBest("password reset",
                Arrays.asList(entry(9, "password reset"), entry(2, "password reset")));
        assertEquals(2L, match.getEntry().getId());
    }

    @Test
    void emptyCorpusUnknownTermsAndStopwordsProduceZeroScores() {
        assertNull(retriever.findBest("alpha", Collections.emptyList()).getEntry());
        assertEquals(0.0, retriever.findBest("alpha", Collections.emptyList()).getScore());
        assertEquals(0.0, retriever.findBest("volcano", Collections.singletonList(entry(1, "password reset"))).getScore());
        assertEquals(0.0, retriever.findBest("how can I", Collections.singletonList(entry(1, "password reset"))).getScore());
        assertEquals(0.0, retriever.findBest("alpha", Collections.singletonList(entry(1, "the and"))).getScore());
    }

    @Test
    void topMatchesOrderByScoreThenIdAndRespectLimit() {
        List<TfIdfRetriever.Match> matches = retriever.findTopMatches("alpha",
                Arrays.asList(entry(8, "alpha beta"), entry(5, "alpha"),
                        entry(1, "gamma"), entry(2, "alpha")), 3);
        assertEquals(Arrays.asList(2L, 5L, 8L), matches.stream()
                .map(match -> match.getEntry().getId()).collect(Collectors.toList()));
        assertEquals(1.0, matches.get(0).getScore(), 1e-12);
        assertTrue(matches.get(2).getScore() > 0 && matches.get(2).getScore() < 1);
        assertEquals(2L, retriever.findTopMatches("alpha",
                Arrays.asList(entry(8, "alpha beta"), entry(2, "alpha")), 1).get(0).getEntry().getId());
    }

    @Test
    void topMatchesReturnAllPositiveCandidatesWithoutApplyingServiceThreshold() {
        List<TfIdfRetriever.Match> matches = retriever.findTopMatches("alpha",
                Collections.singletonList(entry(1, "alpha beta")), 10);
        assertEquals(1, matches.size());
        assertEquals(0.7071067811865475, matches.get(0).getScore(), 1e-12);
    }

    @Test
    void topMatchesExcludeZeroScoresAndHandleEmptyOrNonPositiveLimits() {
        assertTrue(retriever.findTopMatches("alpha", Collections.emptyList(), 3).isEmpty());
        assertTrue(retriever.findTopMatches("unknown", Collections.singletonList(entry(1, "alpha")), 3).isEmpty());
        assertTrue(retriever.findTopMatches("how can I", Collections.singletonList(entry(1, "alpha")), 3).isEmpty());
        assertTrue(retriever.findTopMatches("alpha", Collections.singletonList(entry(1, "the and")), 3).isEmpty());
        assertTrue(retriever.findTopMatches("alpha", Collections.singletonList(entry(1, "alpha")), 0).isEmpty());
        assertTrue(retriever.findTopMatches("alpha", Collections.singletonList(entry(1, "alpha")), -1).isEmpty());
    }

    private FaqEntry entry(long id, String question) {
        FaqEntry entry = new FaqEntry();
        entry.setId(id);
        entry.setQuestion(question);
        return entry;
    }
}
