package com.example.springboot.faq;

import com.example.springboot.faq.config.FaqEmbeddingProperties;
import com.example.springboot.faq.retrieval.EmbeddingClient;
import com.example.springboot.faq.support.FaqRequestBudget;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

import java.io.*;
import java.net.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FaqEmbeddingClientTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test void sendsOnlySelectedModelAndTextsToFixedEndpoint() throws Exception {
        Wire wire = new Wire(response(2));
        double[][] result = client(wire).embed(Arrays.asList("calendar", "contact"), new FaqRequestBudget(35000));
        JsonNode body = json.readTree(wire.out.toByteArray());
        assertEquals("BAAI/bge-m3", body.path("model").asText());
        assertEquals("float", body.path("encoding_format").asText());
        assertEquals(json.readTree("[\"calendar\",\"contact\"]"), body.path("input"));
        assertEquals(3, body.size());
        assertEquals("https://api.siliconflow.cn/v1/embeddings", wire.getURL().toString());
        assertEquals("Bearer fixture-key", wire.getRequestProperty("Authorization"));
        assertFalse(wire.getInstanceFollowRedirects());
        assertEquals("POST", wire.getRequestMethod());
        assertTrue(wire.getConnectTimeout() <= 3000);
        assertTrue(wire.getReadTimeout() <= 3000);
        assertEquals(1024, result[0].length);
        assertEquals(1.0, result[0][0], 1e-12);
        assertEquals(1.0, result[1][1], 1e-12);
        assertTrue(wire.closed);
    }

    @Test void ordinaryConfigIsOfflineAndNeverBorrowsOtherProviderKeys() {
        MockEnvironment env = new MockEnvironment().withProperty("DEEPSEEK_API_KEY", "wrong")
                .withProperty("FAQ_LLM_API_KEY", "wrong");
        assertFalse(new FaqEmbeddingProperties(env).isEnabled());
        env.withProperty("faq.embedding.enabled", "true");
        assertThrows(IllegalArgumentException.class, () -> new FaqEmbeddingProperties(env));
        env.withProperty("SILICONFLOW_API_KEY", "fixture-key");
        assertTrue(new FaqEmbeddingProperties(env).isEnabled());
    }

    @Test void disabledAndCancelledCallsNeverOpenConnection() {
        EmbeddingClient disabled = new EmbeddingClient(new FaqEmbeddingProperties(false, ""), json) {
            @Override protected HttpURLConnection openConnection() { fail("must stay offline"); return null; }
        };
        assertThrows(RuntimeException.class, () -> disabled.embed(Collections.singletonList("x"), new FaqRequestBudget(35000)));
        FaqRequestBudget parent = new FaqRequestBudget(35000);
        FaqRequestBudget child = parent.child(8000);
        parent.cancel();
        assertThrows(FaqRequestBudget.ExpiredException.class, child::checkActive);
    }

    @Test void childCannotOutliveParent() throws Exception {
        FaqRequestBudget parent = new FaqRequestBudget(20);
        FaqRequestBudget child = parent.child(8000);
        Thread.sleep(30);
        assertThrows(FaqRequestBudget.ExpiredException.class, child::checkActive);
    }

    @ParameterizedTest @ValueSource(strings = {"model", "dimension", "duplicate", "zero", "string", "usage", "trailing"})
    void rejectsMalformedProviderData(String mutation) throws Exception {
        ObjectNode body = (ObjectNode) json.readTree(response(2));
        switch (mutation) {
            case "model": body.put("model", "Pro/BAAI/bge-m3"); break;
            case "dimension": ((ArrayNode) body.path("data").get(0).path("embedding")).remove(0); break;
            case "duplicate": ((ObjectNode) body.path("data").get(1)).put("index", 1); break;
            case "zero": ((ArrayNode) body.path("data").get(0).path("embedding")).set(1, json.getNodeFactory().numberNode(0)); break;
            case "string": ((ArrayNode) body.path("data").get(0).path("embedding")).set(0, json.getNodeFactory().textNode("NaN")); break;
            case "usage": ((ObjectNode) body.path("usage")).put("prompt_tokens", new java.math.BigInteger("999999999999999999999")); break;
            default: break;
        }
        byte[] bytes = (body.toString() + (mutation.equals("trailing") ? " {}" : "")).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertThrows(RuntimeException.class, () -> client(new Wire(bytes)).embed(Arrays.asList("a", "b"), new FaqRequestBudget(35000)));
    }

    @Test void normalizesExtremeFiniteVectorsWithoutOverflowOrUnderflow() throws Exception {
        for (double value : new double[]{1e308, 1e-300}) {
            ObjectNode body = (ObjectNode) json.readTree(response(1));
            ArrayNode vector = (ArrayNode) body.path("data").get(0).path("embedding");
            vector.set(0, json.getNodeFactory().numberNode(value));
            vector.set(1, json.getNodeFactory().numberNode(value));
            double[] result = client(new Wire(json.writeValueAsBytes(body))).embed(Collections.singletonList("x"), new FaqRequestBudget(35000))[0];
            assertEquals(Math.sqrt(0.5), result[0], 1e-12);
            assertEquals(Math.sqrt(0.5), result[1], 1e-12);
        }
    }

    @ParameterizedTest @ValueSource(ints = {301, 401, 429, 500})
    void upstreamFailuresAreSanitizedAndNeverRetried(int status) throws Exception {
        Wire wire = new Wire("secret upstream content".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        wire.status = status;
        RuntimeException error = assertThrows(RuntimeException.class, () -> client(wire).embed(Collections.singletonList("x"), new FaqRequestBudget(35000)));
        assertFalse(error.toString().contains("secret"));
        assertNull(error.getCause());
        assertEquals(1, wire.requests);
        assertTrue(wire.closed);
    }

    @Test void rejectsOversizedResponseAndInputsBeforeSending() throws Exception {
        Wire wire = new Wire(new byte[1048577]);
        assertThrows(RuntimeException.class, () -> client(wire).embed(Collections.singletonList("x"), new FaqRequestBudget(35000)));
        for (List<String> input : Arrays.asList(Collections.<String>emptyList(), Collections.nCopies(17, "x"),
                Collections.singletonList(String.join("", Collections.nCopies(3073, "x"))), Collections.singletonList("\u3000"))) {
            Wire unused = new Wire(response(1));
            assertThrows(RuntimeException.class, () -> client(unused).embed(input, new FaqRequestBudget(35000)));
            assertEquals(0, unused.out.size());
        }
    }

    private EmbeddingClient client(Wire wire) {
        return new EmbeddingClient(new FaqEmbeddingProperties(true, "fixture-key"), json) {
            @Override protected HttpURLConnection openConnection() { return wire; }
        };
    }

    private byte[] response(int count) throws Exception {
        ObjectNode body = json.createObjectNode().put("model", "BAAI/bge-m3");
        ArrayNode data = body.putArray("data");
        for (int i = count - 1; i >= 0; i--) {
            ArrayNode vector = data.addObject().put("index", i).putArray("embedding");
            for (int d = 0; d < 1024; d++) vector.add(i == d ? 2 : 0);
        }
        body.putObject("usage").put("prompt_tokens", 2).put("total_tokens", 2);
        return json.writeValueAsBytes(body);
    }

    private static final class Wire extends HttpURLConnection {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final byte[] response;
        int status = 200, requests;
        boolean closed;
        Wire(byte[] response) throws Exception { super(new URL("https://api.siliconflow.cn/v1/embeddings")); this.response = response; }
        @Override public OutputStream getOutputStream() { requests++; return out; }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(response); }
        @Override public int getResponseCode() { return status; }
        @Override public void disconnect() { closed = true; }
        @Override public boolean usingProxy() { return false; }
        @Override public void connect() { }
    }
}
