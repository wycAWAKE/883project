package com.example.springboot.faq.service;

import com.example.springboot.faq.dto.FaqAnswerResponse;
import com.example.springboot.faq.dto.FaqSource;
import com.example.springboot.faq.entity.FaqEntry;
import com.example.springboot.faq.llm.LlmClient;
import com.example.springboot.faq.llm.ConversationTurn;
import com.example.springboot.faq.mapper.FaqMapper;
import com.example.springboot.faq.retrieval.TfIdfRetriever;
import com.example.springboot.faq.retrieval.SemanticFaqRetriever;
import com.example.springboot.faq.support.FaqRequestBudget;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class FaqService {
    private static final int MAX_CONTEXTS = 3;
    private static final int MAX_CONTENT_UNITS = 2000;
    private final FaqMapper mapper;
    private final TfIdfRetriever retriever;
    private final double threshold;
    private final LlmClient llm;
    private final SemanticFaqRetriever semantic;

    public FaqService(FaqMapper mapper, TfIdfRetriever retriever,
                      double threshold, LlmClient llm) {
        this(mapper, retriever, threshold, llm, null);
    }

    @Autowired
    public FaqService(FaqMapper mapper, TfIdfRetriever retriever,
                      @Value("${faq.match-threshold:0.35}") double threshold, LlmClient llm,
                      SemanticFaqRetriever semantic) {
        if (!Double.isFinite(threshold) || threshold <= 0 || threshold > 1) {
            throw new IllegalArgumentException("FAQ match threshold must be greater than 0 and at most 1.");
        }
        this.mapper = mapper;
        this.retriever = retriever;
        this.threshold = threshold;
        this.llm = llm;
        this.semantic = semantic;
    }

    public FaqAnswerResponse ask(String question) {
        return ask(question, new FaqRequestBudget(35000));
    }

    public ChatAnswer chat(String question, List<ConversationTurn> history, FaqRequestBudget budget) {
        budget.checkActive();
        if (!LlmClient.needsContext(question)) {
            budget.setConversationContext(question, true);
            return new ChatAnswer(ask(question, budget), question, true);
        }
        FaqAnswerResponse clarification = clarification();
        // A rewrite timeout has no current evidence; never fall back to the previous turn's facts.
        budget.publishFallback(clarification);
        if (history.isEmpty() || !llm.isEnabled() || history.stream().noneMatch(turn ->
                "generated".equals(turn.getMode()) || "extractive".equals(turn.getMode()))) {
            return new ChatAnswer(clarification, question, false);
        }
        LlmClient.Resolution resolution;
        try {
            resolution = llm.resolve(question, history, budget);
            budget.checkActive();
        } catch (RuntimeException failure) {
            return new ChatAnswer(clarification, null, false); // Failed/incomplete processing does not enter history.
        }
        if (resolution.isClarification()) return new ChatAnswer(clarification, question, false);
        budget.setConversationContext(resolution.getQuestion(), resolution.isNewTopic());
        return new ChatAnswer(ask(resolution.getQuestion(), budget), resolution.getQuestion(), resolution.isNewTopic());
    }

    public static FaqAnswerResponse clarification() {
        return new FaqAnswerResponse(false, null, null, null, null, 0.0,
                "Please name the campus service, department or document you mean, and ask a complete question.",
                "clarify", Collections.emptyList(), null);
    }

    /** Internal orchestration result; only answer is copied to the public chat DTO. */
    public static final class ChatAnswer {
        private final FaqAnswerResponse answer;
        private final String question;
        private final boolean newTopic;
        public ChatAnswer(FaqAnswerResponse answer, String question, boolean newTopic) {
            this.answer = answer; this.question = question; this.newTopic = newTopic;
        }
        public FaqAnswerResponse getAnswer() { return answer; }
        public String getQuestion() { return question; }
        public boolean isNewTopic() { return newTopic; }
    }

    public FaqAnswerResponse ask(String question, FaqRequestBudget budget) {
        budget.checkActive();
        // A database deadline is unavailability, not a successful absence of evidence.
        List<FaqEntry> entries = currentEvidence(budget);
        boolean semanticAdmission = semantic != null && semantic.isSemanticAcceptanceEnabled();
        // Publish before any potentially blocking enablement/retrieval work; candidates are not answers.
        if (semanticAdmission) budget.publishFallback(noMatch());
        boolean generationEnabled = semanticAdmission && llm.isEnabled();
        List<TfIdfRetriever.Match> matches = findEligibleMatches(question, entries, budget, generationEnabled);
        budget.checkActive();
        if (matches.isEmpty()) {
            return noMatch();
        }
        if (!semanticAdmission) generationEnabled = llm.isEnabled();
        // Similarity alone does not prove the requested fact; strict-mode generation failures fail closed.
        FaqAnswerResponse fallback = semanticAdmission && generationEnabled ? noMatch() : storedAnswer(matches.get(0));
        budget.publishFallback(fallback);
        if (!generationEnabled) return fallback;
        try {
            List<FaqEntry> contexts = matches.stream().map(match -> contextCopy(match.getEntry())).collect(Collectors.toList());
            LlmClient.Generation result = llm.generate(question, contexts, budget);
            if (result.isInsufficientEvidence()) {
                FaqAnswerResponse refusal = noMatch();
                budget.publishFallback(refusal);
                return refusal;
            }
            budget.checkActive();
            Set<Long> citedIds = new HashSet<>(result.getCitedFaqIds());
            List<TfIdfRetriever.Match> cited = matches.stream()
                    .filter(match -> citedIds.contains(match.getEntry().getId())).collect(Collectors.toList());
            if (cited.isEmpty() || cited.size() != citedIds.size()) return fallback;
            return response("generated", result.getAnswer(), cited, null);
        } catch (RuntimeException failure) {
            // Model-only boundary: no upstream details, causes or prompt data escape to callers/logs.
            return fallback;
        }
    }

    private FaqAnswerResponse response(String mode, String answer, List<TfIdfRetriever.Match> matches, String notice) {
        FaqEntry first = matches.get(0).getEntry();
        List<FaqSource> sources = matches.stream().map(match -> new FaqSource(match.getEntry().getId(),
                match.getEntry().getSourceTitle(), match.getEntry().getSourceUrl())).collect(Collectors.toList());
        return new FaqAnswerResponse(true, first.getId(), answer, first.getSourceTitle(),
                first.getSourceUrl(), matches.get(0).getScore(), null, mode,
                Collections.unmodifiableList(sources), notice);
    }

    public static FaqAnswerResponse noMatch() {
        return new FaqAnswerResponse(false, null, null, null, null, 0,
                "No matching answer was found. Please contact student support.", "no_match", Collections.emptyList(), null);
    }

    private FaqAnswerResponse storedAnswer(TfIdfRetriever.Match match) {
        return response("extractive", match.getEntry().getAnswer(), Collections.singletonList(match),
                "AI generation is unavailable. Showing the stored FAQ answer.");
    }

    /** Internal RAG evidence, not an additional HTTP endpoint. Stored answers remain untouched. */
    public List<TfIdfRetriever.Match> retrieveContexts(String question) {
        FaqRequestBudget budget = new FaqRequestBudget(35000);
        return findEligibleMatches(question, currentEvidence(budget), budget, false).stream()
                .map(match -> new TfIdfRetriever.Match(contextCopy(match.getEntry()), match.getScore()))
                .collect(Collectors.toList());
    }

    private List<FaqEntry> currentEvidence(FaqRequestBudget budget) {
        List<FaqEntry> entries = mapper.findEnabled().stream()
                .filter(this::isEvidence).collect(Collectors.toList());
        budget.checkActive();
        return entries;
    }

    private List<TfIdfRetriever.Match> findEligibleMatches(String question, List<FaqEntry> entries,
                                                        FaqRequestBudget budget, boolean generationEnabled) {
        // Preserve the lexical gate for legacy mode and provider-unavailable context fallback.
        List<TfIdfRetriever.Match> admitted = retriever.findTopMatches(question, entries, entries.size()).stream()
                .filter(match -> match.getScore() >= threshold).collect(Collectors.toList());
        if (semantic != null && semantic.isSemanticAcceptanceEnabled()) {
            // Until semantic qualification completes, no lexical answer may escape through timeout fallback.
            budget.publishFallback(noMatch());
            SemanticFaqRetriever.Result result = generationEnabled
                    ? semantic.searchCandidates(question, entries, budget) : semantic.search(question, entries, budget);
            if (result.isAvailable()) return result.getMatches();
            if (!generationEnabled && !admitted.isEmpty()) budget.publishFallback(storedAnswer(admitted.get(0)));
            return admitted.stream().limit(MAX_CONTEXTS).collect(Collectors.toList());
        }
        // Preserve current evidence even when embedding exhausts a short remaining parent deadline.
        if (!admitted.isEmpty()) budget.publishFallback(storedAnswer(admitted.get(0)));
        return semantic == null ? admitted.stream().limit(MAX_CONTEXTS).collect(Collectors.toList())
                : semantic.rank(question, entries, admitted, budget);
    }

    private boolean isEvidence(FaqEntry entry) {
        return entry != null && entry.getId() != null && entry.getId() > 0
                && Boolean.TRUE.equals(entry.getEnabled()) && hasText(entry.getQuestion())
                && entry.getQuestion().length() < MAX_CONTENT_UNITS && hasText(entry.getAnswer())
                && hasText(shorten(entry.getAnswer(), MAX_CONTENT_UNITS - entry.getQuestion().length()))
                && hasText(entry.getSourceTitle()) && isOfficialSource(entry.getSourceUrl());
    }

    private boolean hasText(String text) {
        return text != null && text.codePoints().anyMatch(
                code -> !Character.isWhitespace(code) && !Character.isSpaceChar(code));
    }

    private boolean isOfficialSource(String url) {
        if (url == null) {
            return false;
        }
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            if (host == null || uri.getRawUserInfo() != null
                    || !("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))) {
                return false;
            }
            host = host.toLowerCase(Locale.ROOT);
            return host.equals("ccnu.edu.cn") || host.endsWith(".ccnu.edu.cn");
        } catch (URISyntaxException exception) {
            return false;
        }
    }

    private FaqEntry contextCopy(FaqEntry entry) {
        FaqEntry context = new FaqEntry();
        context.setId(entry.getId());
        context.setQuestion(entry.getQuestion());
        context.setAnswer(shorten(entry.getAnswer(), MAX_CONTENT_UNITS - entry.getQuestion().length()));
        context.setSourceTitle(entry.getSourceTitle());
        context.setSourceUrl(entry.getSourceUrl());
        context.setEnabled(true);
        context.setUpdatedAt(entry.getUpdatedAt());
        return context;
    }

    private String shorten(String text, int limit) {
        int end = Math.min(text.length(), limit);
        if (end > 0 && end < text.length() && Character.isHighSurrogate(text.charAt(end - 1))
                && Character.isLowSurrogate(text.charAt(end))) {
            end--;
        }
        return text.substring(0, end);
    }
}
