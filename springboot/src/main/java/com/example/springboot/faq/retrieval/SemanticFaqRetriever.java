package com.example.springboot.faq.retrieval;

import com.example.springboot.faq.entity.FaqEntry;
import com.example.springboot.faq.support.FaqRequestBudget;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/** Small process-local index. Evidence always comes from the request's current filtered DB snapshot. */
@Component
public class SemanticFaqRetriever {
    private final EmbeddingClient client;
    private final ReentrantLock rebuild = new ReentrantLock();
    private volatile Snapshot snapshot;
    private volatile long unavailableUntil;

    private final boolean semanticAcceptanceEnabled;
    private final double semanticThreshold;
    private final double candidateThreshold;
    public SemanticFaqRetriever(EmbeddingClient client) { this(client, false, .73); }
    public SemanticFaqRetriever(EmbeddingClient client, boolean enabled, double threshold) {
        this(client, enabled, threshold, threshold);
    }
    @Autowired
    public SemanticFaqRetriever(EmbeddingClient client,
            @Value("${faq.embedding.semantic-acceptance-enabled:false}") boolean enabled,
            @Value("${faq.embedding.semantic-threshold:0.73}") double threshold,
            @Value("${faq.embedding.candidate-threshold:${faq.embedding.semantic-threshold:0.73}}") double candidateThreshold) {
        if (!Double.isFinite(threshold) || threshold <= 0 || threshold > 1) throw new IllegalArgumentException("Invalid semantic threshold.");
        if (!Double.isFinite(candidateThreshold) || candidateThreshold <= 0 || candidateThreshold > threshold)
            throw new IllegalArgumentException("Invalid candidate threshold.");
        this.client = client; this.semanticAcceptanceEnabled = enabled; this.semanticThreshold = threshold;
        this.candidateThreshold = candidateThreshold;
    }
    public boolean isSemanticAcceptanceEnabled() { return semanticAcceptanceEnabled; }
    public static final class Result {
        private final boolean available;
        private final List<TfIdfRetriever.Match> matches;
        private Result(boolean available, List<TfIdfRetriever.Match> matches) { this.available=available; this.matches=Collections.unmodifiableList(matches); }
        public boolean isAvailable() { return available; }
        public List<TfIdfRetriever.Match> getMatches() { return matches; }
    }
    public Result search(String question, List<FaqEntry> current, FaqRequestBudget budget) {
        return search(question, current, budget, semanticThreshold);
    }

    /** Candidates require a successful grounded generation; never use them directly as stored answers. */
    public Result searchCandidates(String question, List<FaqEntry> current, FaqRequestBudget budget) {
        if (candidateThreshold == semanticThreshold) return search(question, current, budget);
        return search(question, current, budget, candidateThreshold);
    }

    private Result search(String question, List<FaqEntry> current, FaqRequestBudget budget, double minimumScore) {
        List<TfIdfRetriever.Match> all = current.stream().map(e -> new TfIdfRetriever.Match(e, 1)).collect(Collectors.toList());
        return lookup(question, current, all, budget, true, minimumScore);
    }

    public List<TfIdfRetriever.Match> rank(String question, List<FaqEntry> current,
                                          List<TfIdfRetriever.Match> admitted, FaqRequestBudget parent) {
        return lookup(question, current, admitted, parent, false, 0).getMatches();
    }
    private Result lookup(String question, List<FaqEntry> current, List<TfIdfRetriever.Match> admitted,
                          FaqRequestBudget parent, boolean strict, double minimumScore) {
        parent.checkActive();
        List<TfIdfRetriever.Match> fallback = new ArrayList<>(admitted.subList(0, Math.min(3, admitted.size())));
        if (current.isEmpty()) snapshot = null;
        if (current.isEmpty()) return new Result(true, Collections.emptyList());
        if (!client.isEnabled() || admitted.isEmpty() || System.nanoTime() < unavailableUntil) return new Result(false, fallback);
        try {
            if (current.size() > 128) return new Result(false, fallback);
            List<FaqEntry> ordered = current.stream().sorted(Comparator.comparing(FaqEntry::getId)).collect(Collectors.toList());
            List<List<String>> batches = batches(ordered);
            String version = fingerprint(ordered);
            FaqRequestBudget budget = parent.child(8000);
            Snapshot active = snapshot;
            if (active == null || !active.version.equals(version)) {
                if (!rebuild.tryLock()) return new Result(false, fallback);
                try {
                    active = snapshot;
                    if (active == null || !active.version.equals(version)) {
                        snapshot = null;
                        List<double[]> vectors = new ArrayList<>();
                        for (List<String> batch : batches) {
                            budget.checkActive();
                            vectors.addAll(Arrays.asList(client.embed(batch, budget)));
                        }
                        if (vectors.size() != ordered.size()) throw new IllegalArgumentException();
                        Map<Long, double[]> index = new HashMap<>();
                        for (int i = 0; i < ordered.size(); i++) index.put(ordered.get(i).getId(), vectors.get(i));
                        budget.checkActive();
                        active = new Snapshot(version, Collections.unmodifiableMap(index));
                        snapshot = active;
                    }
                } finally { rebuild.unlock(); }
            }
            double[] query = client.embed(Collections.singletonList(question), budget)[0];
            budget.checkActive();
            List<TfIdfRetriever.Match> ranked = new ArrayList<>();
            for (TfIdfRetriever.Match candidate : admitted) {
                double score = cosine(query, active.vectors.get(candidate.getEntry().getId()));
                if (strict ? score >= minimumScore : score > 0) ranked.add(new TfIdfRetriever.Match(candidate.getEntry(), score));
            }
            ranked.sort(Comparator.comparingDouble(TfIdfRetriever.Match::getScore).reversed()
                    .thenComparing(m -> m.getEntry().getId()));
            return new Result(true, ranked.isEmpty() && !strict ? fallback : new ArrayList<>(ranked.subList(0, Math.min(3, ranked.size()))));
        } catch (RuntimeException unavailable) {
            // A child timeout may fall back; a cancelled/expired whole request must stop.
            parent.checkActive();
            unavailableUntil = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            return new Result(false, fallback);
        }
    }

    private List<List<String>> batches(List<FaqEntry> entries) {
        List<List<String>> batches = new ArrayList<>();
        List<String> batch = new ArrayList<>();
        int bytes = 0, total = 0;
        for (FaqEntry row : entries) {
            String text = row.getQuestion() + " " + (row.getKeywords() == null ? "" : row.getKeywords());
            int size = text.getBytes(StandardCharsets.UTF_8).length;
            if (size > EmbeddingClient.MAX_INPUT_BYTES) throw new IllegalArgumentException();
            total += size;
            if (total > 24576) throw new IllegalArgumentException();
            if (!batch.isEmpty() && (batch.size() == 16 || bytes + size > EmbeddingClient.MAX_INPUT_BYTES)) {
                batches.add(batch); batch = new ArrayList<>(); bytes = 0;
            }
            batch.add(text); bytes += size;
        }
        if (!batch.isEmpty()) batches.add(batch);
        if (batches.size() > 8) throw new IllegalArgumentException();
        return batches;
    }

    private String fingerprint(List<FaqEntry> entries) {
        try {
            MessageDigest hash = MessageDigest.getInstance("SHA-256");
            update(hash, EmbeddingClient.MODEL, EmbeddingClient.DIMENSIONS, "question-keywords-v1");
            for (FaqEntry row : entries) {
                update(hash, row.getId(), row.getQuestion(), row.getAnswer(), row.getCategory(), row.getKeywords(),
                        row.getSourceTitle(), row.getSourceUrl(), row.getEnabled(), row.getUpdatedAt());
            }
            return Base64.getEncoder().encodeToString(hash.digest());
        } catch (NoSuchAlgorithmException failure) { throw new IllegalStateException("SHA-256 unavailable."); }
    }

    private void update(MessageDigest hash, Object... values) {
        for (Object value : values) {
            byte[] bytes = value == null ? new byte[0] : value.toString().getBytes(StandardCharsets.UTF_8);
            hash.update(ByteBuffer.allocate(4).putInt(value == null ? -1 : bytes.length).array());
            hash.update(bytes);
        }
    }

    private double cosine(double[] left, double[] right) {
        if (left == null || right == null || left.length != EmbeddingClient.DIMENSIONS || right.length != left.length)
            throw new IllegalArgumentException();
        double score = 0;
        for (int i = 0; i < left.length; i++) score += left[i] * right[i];
        if (!Double.isFinite(score)) throw new IllegalArgumentException();
        return Math.max(-1, Math.min(1, score));
    }

    private static final class Snapshot {
        final String version;
        final Map<Long, double[]> vectors;
        Snapshot(String version, Map<Long, double[]> vectors) { this.version = version; this.vectors = vectors; }
    }
}
