package com.example.springboot.faq.retrieval;

import com.example.springboot.faq.config.FaqEmbeddingProperties;
import com.example.springboot.faq.support.FaqRequestBudget;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Fixed-provider, bounded, non-retrying embedding transport. No prompt/history or response logging. */
@Component
public class EmbeddingClient {
    public static final String ENDPOINT = "https://api.siliconflow.cn/v1/embeddings";
    public static final String MODEL = "BAAI/bge-m3";
    public static final int DIMENSIONS = 1024;
    public static final int MAX_INPUT_BYTES = 3072;
    private static final int MAX_RESPONSE_BYTES = 1048576;
    private final FaqEmbeddingProperties properties;
    private final ObjectMapper json;

    public EmbeddingClient(FaqEmbeddingProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.json = mapper.copy().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    }

    public boolean isEnabled() { return properties.isEnabled(); }

    public double[][] embed(List<String> input, FaqRequestBudget budget) {
        budget.checkActive();
        if (!isEnabled() || input == null || input.isEmpty() || input.size() > 16) throw invalid();
        ObjectNode request = json.createObjectNode().put("model", MODEL).put("encoding_format", "float");
        ArrayNode texts = request.putArray("input");
        int inputBytes = 0;
        for (String text : input) {
            if (text == null || !text.codePoints().anyMatch(c -> !Character.isWhitespace(c) && !Character.isSpaceChar(c))) throw invalid();
            inputBytes += text.getBytes(StandardCharsets.UTF_8).length;
            if (inputBytes > MAX_INPUT_BYTES) throw invalid();
            texts.add(text);
        }
        HttpURLConnection connection = null;
        try {
            byte[] outgoing = json.writeValueAsBytes(request);
            budget.checkActive();
            connection = openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(budget.socketTimeoutMillis(3000));
            connection.setReadTimeout(budget.socketTimeoutMillis(3000));
            connection.setFixedLengthStreamingMode(outgoing.length);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + properties.getApiKey());
            budget.checkActive();
            try (OutputStream stream = connection.getOutputStream()) { stream.write(outgoing); }
            budget.checkActive();
            connection.setReadTimeout(budget.socketTimeoutMillis(3000));
            // shortcut: DNS/writes/slow headers may outlive this cooperative budget; add abortable transport if observed.
            if (connection.getResponseCode() != 200) throw invalid();
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            try (InputStream stream = connection.getInputStream()) {
                byte[] buffer = new byte[4096];
                while (true) {
                    connection.setReadTimeout(budget.socketTimeoutMillis(3000));
                    int count = stream.read(buffer);
                    budget.checkActive();
                    if (count < 0) break;
                    if (body.size() + count > MAX_RESPONSE_BYTES) throw invalid();
                    body.write(buffer, 0, count);
                }
            }
            double[][] vectors = parse(body.toByteArray(), input.size());
            budget.checkActive();
            return vectors;
        } catch (IOException failure) {
            throw invalid();
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    protected HttpURLConnection openConnection() throws IOException {
        return (HttpURLConnection) new URL(ENDPOINT).openConnection();
    }

    private double[][] parse(byte[] bytes, int expected) throws IOException {
        JsonNode body = json.readTree(bytes);
        if (body == null || !MODEL.equals(body.path("model").asText())
                || !body.path("data").isArray() || body.path("data").size() != expected) throw invalid();
        JsonNode prompt = body.path("usage").path("prompt_tokens"), total = body.path("usage").path("total_tokens");
        if (!prompt.isIntegralNumber() || !prompt.canConvertToLong() || !total.isIntegralNumber() || !total.canConvertToLong()
                || prompt.longValue() <= 0 || prompt.longValue() > 40000 || total.longValue() != prompt.longValue()) throw invalid();
        double[][] result = new double[expected][];
        for (JsonNode item : body.path("data")) {
            JsonNode index = item.path("index"), vector = item.path("embedding");
            if (!index.isIntegralNumber() || !index.canConvertToInt() || index.intValue() < 0 || index.intValue() >= expected
                    || result[index.intValue()] != null || !vector.isArray() || vector.size() != DIMENSIONS) throw invalid();
            double[] values = new double[DIMENSIONS];
            double scale = 0;
            for (int d = 0; d < DIMENSIONS; d++) {
                if (!vector.get(d).isNumber() || !Double.isFinite(vector.get(d).doubleValue())) throw invalid();
                values[d] = vector.get(d).doubleValue();
                scale = Math.max(scale, Math.abs(values[d]));
            }
            if (scale == 0) throw invalid();
            double norm = 0;
            for (int d = 0; d < DIMENSIONS; d++) { values[d] /= scale; norm += values[d] * values[d]; }
            norm = Math.sqrt(norm);
            for (int d = 0; d < DIMENSIONS; d++) values[d] /= norm;
            result[index.intValue()] = values;
        }
        return result;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("FAQ embedding is unavailable or returned invalid data.");
    }
}
