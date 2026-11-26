package com.example.springboot.faq;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Step4 pilot contract checks, not a production embedding client/index. */
class FaqEmbeddingSelectionTest {
    @Test void batchesAndRequestsStayWithinTheApprovedPilotBounds() throws Exception {
        List<String> input = Arrays.asList("Academic calendar", "Network access", "Museum admission");
        byte[] bytes = EmbeddingSelectionProbe.request(input);
        JsonNode body = new ObjectMapper().readTree(bytes);
        assertEquals("BAAI/bge-m3", body.path("model").asText());
        assertFalse(body.has("dimensions")); // This model does not support dimension overrides.
        assertEquals("float", body.path("encoding_format").asText());
        assertEquals(3, body.path("input").size());
        assertEquals(3, body.size()); // Credentials and chat/identity parameters cannot enter JSON.
        List<String> many = Collections.nCopies(33, "A public question");
        List<List<String>> batches = EmbeddingSelectionProbe.batches(many);
        assertEquals(Arrays.asList(16,16,1), Arrays.asList(batches.get(0).size(),batches.get(1).size(),batches.get(2).size()));
        assertThrows(IllegalArgumentException.class, () -> EmbeddingSelectionProbe.request(Collections.singletonList("")));
        assertThrows(IllegalArgumentException.class, () -> EmbeddingSelectionProbe.request(Collections.nCopies(17,"x")));
        assertThrows(IllegalArgumentException.class, () -> EmbeddingSelectionProbe.request(Collections.singletonList(String.join("",Collections.nCopies(3073,"x")))));
    }

    @Test void responseIndicesRestoreInputOrderAndReportInputTokens() throws Exception {
        ObjectNode response = response();
        EmbeddingSelectionProbe.Batch result = EmbeddingSelectionProbe.parse(new ObjectMapper().writeValueAsBytes(response),2);
        assertEquals(12,result.tokens);
        assertEquals(1024,result.vectors[0].length);
        assertEquals(1.0,result.vectors[0][0]);
        assertEquals(1.0,result.vectors[1][1]);
        assertEquals(0,EmbeddingSelectionProbe.cosine(result.vectors[0],result.vectors[1]),1e-12);
        assertEquals(1,EmbeddingSelectionProbe.cosine(result.vectors[0],result.vectors[0]),1e-12);
    }

    @ParameterizedTest @ValueSource(strings={"dimension","duplicate","missing","index","nan","zero","model","usage","negative_usage","overflow_prompt","overflow_total"})
    void malformedOrMismatchedVectorsAreRejected(String scenario) throws Exception {
        ObjectNode body=response();
        ObjectNode first=(ObjectNode)body.path("data").get(0);
        if("dimension".equals(scenario)) ((ArrayNode)first.path("embedding")).remove(0);
        if("duplicate".equals(scenario)) first.put("index",0);
        if("missing".equals(scenario)) ((ArrayNode)body.path("data")).remove(0);
        if("index".equals(scenario)) first.put("index",2);
        if("nan".equals(scenario)) ((ArrayNode)first.path("embedding")).set(0,DoubleNode.valueOf(Double.POSITIVE_INFINITY));
        if("zero".equals(scenario)) ((ArrayNode)first.path("embedding")).set(1,DoubleNode.valueOf(0));
        if("model".equals(scenario)) body.put("model","other-model");
        if("usage".equals(scenario)) body.remove("usage");
        if("negative_usage".equals(scenario)) ((ObjectNode)body.path("usage")).put("total_tokens",-1);
        if("overflow_prompt".equals(scenario)) ((ObjectNode)body.path("usage")).put("prompt_tokens",new java.math.BigInteger("18446744073709551628"));
        if("overflow_total".equals(scenario)) ((ObjectNode)body.path("usage")).put("total_tokens",new java.math.BigInteger("18446744073709551628"));
        byte[] bytes=new ObjectMapper().writeValueAsBytes(body);
        assertThrows(IllegalArgumentException.class,()->EmbeddingSelectionProbe.parse(bytes,2));
    }

    private ObjectNode response() {
        ObjectMapper json=new ObjectMapper();
        ObjectNode body=json.createObjectNode().put("model","BAAI/bge-m3");
        body.putObject("usage").put("prompt_tokens",12).put("total_tokens",12);
        ArrayNode data=body.putArray("data");
        for(int index:new int[]{1,0}) {
            ArrayNode vector=data.addObject().put("index",index).putArray("embedding");
            for(int d=0;d<1024;d++)vector.add(d==index?1.0:0.0);
        }
        return body;
    }
}
