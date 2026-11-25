package com.example.springboot.faq.retrieval;

import com.example.springboot.faq.entity.FaqEntry;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** English lexical retrieval; see FAQ_CONSTRAINTS.md before modifying the algorithm. */
@Component
public class TfIdfRetriever {
    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}]+");
    private static final Set<String> STOPWORDS = new HashSet<>(Arrays.asList(
            "a", "an", "the", "to", "is", "are", "do", "does", "of", "and", "or", "for", "in", "on",
            "my", "me", "i", "we", "you", "your", "what", "how", "can", "where", "when", "with", "it",
            "this", "that", "be", "at", "from"));

    public Match findBest(String question, List<FaqEntry> entries) {
        List<Match> matches = findTopMatches(question, entries, 1);
        return matches.isEmpty() ? new Match(null, 0.0) : matches.get(0);
    }

    /** Positive-score candidates only; evidence validity and thresholds belong to the service. */
    public List<Match> findTopMatches(String question, List<FaqEntry> entries, int limit) {
        if (limit <= 0 || entries.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Integer>> documents = new ArrayList<>();
        Map<String, Integer> documentFrequency = new HashMap<>();
        for (FaqEntry entry : entries) {
            Map<String, Integer> terms = frequencies(entry.getQuestion() + " "
                    + (entry.getKeywords() == null ? "" : entry.getKeywords()));
            documents.add(terms);
            terms.keySet().forEach(term -> documentFrequency.merge(term, 1, Integer::sum));
        }

        Map<String, Double> idf = new HashMap<>();
        documentFrequency.forEach((term, count) ->
                idf.put(term, Math.log((entries.size() + 1.0) / (count + 1.0)) + 1.0));
        Map<String, Double> queryVector = vector(frequencies(question), idf);
        List<Match> matches = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            double score = cosine(queryVector, vector(documents.get(i), idf));
            if (score > 0) {
                matches.add(new Match(entries.get(i), score));
            }
        }
        matches.sort(Comparator.comparingDouble(Match::getScore).reversed()
                .thenComparing(match -> match.getEntry().getId()));
        return new ArrayList<>(matches.subList(0, Math.min(limit, matches.size())));
    }

    private Map<String, Integer> frequencies(String text) {
        Map<String, Integer> frequencies = new HashMap<>();
        Matcher matcher = TOKEN.matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String term = matcher.group();
            if (!STOPWORDS.contains(term)) {
                frequencies.merge(term, 1, Integer::sum);
            }
        }
        return frequencies;
    }

    private Map<String, Double> vector(Map<String, Integer> terms, Map<String, Double> idf) {
        Map<String, Double> vector = new HashMap<>();
        terms.forEach((term, count) -> {
            if (idf.containsKey(term)) {
                vector.put(term, count * idf.get(term));
            }
        });
        return vector;
    }

    private double cosine(Map<String, Double> left, Map<String, Double> right) {
        double leftNorm = 0;
        double rightNorm = 0;
        double dot = 0;
        for (Map.Entry<String, Double> term : left.entrySet()) {
            leftNorm += term.getValue() * term.getValue();
            dot += term.getValue() * right.getOrDefault(term.getKey(), 0.0);
        }
        for (double weight : right.values()) {
            rightNorm += weight * weight;
        }
        if (leftNorm == 0 || rightNorm == 0) {
            return 0;
        }
        double score = dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
        // Normalize floating-point roundoff so an exact match also passes threshold=1.
        return score >= 1.0 - 1e-12 ? 1.0 : score;
    }

    @Getter
    @AllArgsConstructor
    public static class Match {
        private final FaqEntry entry;
        private final double score;
    }
}
