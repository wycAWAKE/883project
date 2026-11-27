package com.example.springboot.faq;

import java.security.*;
import java.util.*;
import java.util.stream.Collectors;

/** Offline calibration math only; never loaded by the application. */
final class ThresholdCalibration {
    static final class Hit {
        public final String key;
        public final double score;
        Hit(String key, double score) { this.key = key; this.score = score; }
    }
    static final class ScoredCase {
        public final String id, outcome;
        public final Set<String> expectedKeys;
        public final List<Hit> ranked;
        ScoredCase(String id, String outcome, Set<String> expectedKeys, List<Hit> ranked) {
            this.id = id; this.outcome = outcome; this.expectedKeys = expectedKeys; this.ranked = ranked;
        }
    }
    static final class Metrics {
        public int positives, negatives, clarifications, correctTop1Accepted, wrongTop1Accepted,
                negativeAccepted, positiveRejected, acceptedPositiveWithoutTop3Support, top3Supported;
    }
    static final class Row {
        public final double threshold;
        public final Metrics metrics;
        Row(double threshold, Metrics metrics) { this.threshold = threshold; this.metrics = metrics; }
    }

    static double cosine(double[] left, double[] right) {
        if (left == null || right == null || left.length != right.length || left.length == 0) throw invalid();
        double[] a = unit(left), b = unit(right);
        double score = 0;
        for (int i = 0; i < a.length; i++) score += a[i] * b[i];
        return Math.max(-1, Math.min(1, score));
    }
    private static double[] unit(double[] values) {
        double scale = 0;
        for (double value : values) { if (!Double.isFinite(value)) throw invalid(); scale = Math.max(scale, Math.abs(value)); }
        if (scale == 0) throw invalid();
        double[] result = values.clone(); double norm = 0;
        for (int i = 0; i < result.length; i++) { result[i] /= scale; norm += result[i] * result[i]; }
        norm = Math.sqrt(norm);
        for (int i = 0; i < result.length; i++) result[i] /= norm;
        return result;
    }
    static List<Hit> rank(List<String> keys, List<double[]> documents, double[] query) {
        if (keys.isEmpty() || keys.size() != documents.size() || new HashSet<>(keys).size() != keys.size()) throw invalid();
        List<Hit> result = new ArrayList<>();
        for (int i = 0; i < keys.size(); i++) result.add(new Hit(keys.get(i), cosine(query, documents.get(i))));
        result.sort(Comparator.comparingDouble((Hit h) -> h.score).reversed().thenComparing(h -> h.key));
        return result;
    }
    static Metrics measure(List<ScoredCase> cases, double threshold) {
        if (!Double.isFinite(threshold) || threshold < 0 || threshold > 1 || cases.isEmpty()) throw invalid();
        Metrics stats = new Metrics(); Set<String> ids = new HashSet<>();
        for (ScoredCase row : cases) {
            if (row.id == null || row.id.isEmpty() || !ids.add(row.id) || row.expectedKeys == null || row.ranked == null) throw invalid();
            if ("clarify".equals(row.outcome)) {
                if (!row.expectedKeys.isEmpty() || !row.ranked.isEmpty()) throw invalid();
                stats.clarifications++; continue;
            }
            boolean positive = "answerable".equals(row.outcome);
            if ((!positive && !"no_match".equals(row.outcome)) || positive == row.expectedKeys.isEmpty() || row.ranked.isEmpty()) throw invalid();
            Set<String> keys = new HashSet<>(); double previous = Double.POSITIVE_INFINITY; String previousKey = "";
            for (Hit hit : row.ranked) {
                if (hit.key == null || hit.key.isEmpty() || !keys.add(hit.key) || !Double.isFinite(hit.score)
                        || hit.score < -1 || hit.score > 1 || hit.score > previous
                        || (hit.score == previous && hit.key.compareTo(previousKey) < 0)) throw invalid();
                previous = hit.score; previousKey = hit.key;
            }
            if (!keys.containsAll(row.expectedKeys)) throw invalid();
            List<Hit> accepted = row.ranked.stream().filter(h -> h.score >= threshold).limit(3).collect(Collectors.toList());
            if (!positive) { stats.negatives++; if (!accepted.isEmpty()) stats.negativeAccepted++; continue; }
            stats.positives++;
            if (accepted.isEmpty()) { stats.positiveRejected++; continue; }
            if (row.expectedKeys.contains(accepted.get(0).key)) stats.correctTop1Accepted++; else stats.wrongTop1Accepted++;
            if (accepted.stream().anyMatch(h -> row.expectedKeys.contains(h.key))) stats.top3Supported++;
            else stats.acceptedPositiveWithoutTop3Support++;
        }
        return stats;
    }
    static List<Row> sweep(List<ScoredCase> cases) {
        List<Row> rows = new ArrayList<>();
        for (int i = 1; i <= 100; i++) rows.add(new Row(i / 100.0, measure(cases, i / 100.0)));
        return rows;
    }
    static List<Hit> step5Ranking(List<Hit> semantic, Map<String, Double> lexical) {
        boolean hasPositive = semantic.stream().anyMatch(h -> lexical.getOrDefault(h.key, 0.0) >= .35 && h.score > 0);
        List<Hit> result = new ArrayList<>();
        for (Hit hit : semantic) {
            double score = lexical.getOrDefault(hit.key, 0.0);
            result.add(new Hit(hit.key, score >= .35 && (!hasPositive || hit.score > 0) ? (hasPositive ? hit.score : score) : -1));
        }
        result.sort(Comparator.comparingDouble((Hit h) -> h.score).reversed().thenComparing(h -> h.key));
        return result;
    }
    static Row select(List<Row> rows) {
        Row best = null;
        for (Row row : rows) {
            if (row.metrics.negativeAccepted != 0 || row.metrics.wrongTop1Accepted != 0) continue;
            if (best == null || row.metrics.correctTop1Accepted > best.metrics.correctTop1Accepted
                    || (row.metrics.correctTop1Accepted == best.metrics.correctTop1Accepted && row.threshold < best.threshold)) best = row;
        }
        return best;
    }
    static String sha256(byte[] bytes) {
        try {
            StringBuilder hash = new StringBuilder();
            for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes)) hash.append(String.format(Locale.ROOT, "%02x", value & 255));
            return hash.toString();
        } catch (NoSuchAlgorithmException failure) { throw new IllegalStateException(failure); }
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid calibration data."); }
}
