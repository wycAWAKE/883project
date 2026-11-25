package com.example.springboot.faq;

import com.example.springboot.faq.support.FaqRequestBudget;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Step4 experiment only. Step5 must define production indexing, invalidation and fallback separately. */
final class EmbeddingSelectionProbe {
    static final String ENDPOINT="https://api.siliconflow.cn/v1/embeddings";
    static final String MODEL="BAAI/bge-m3";
    static final int DIMENSIONS=1024;
    private static final ObjectMapper JSON=new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);

    static List<List<String>> batches(List<String> input) {
        List<List<String>> result=new ArrayList<>();
        List<String> current=new ArrayList<>(); int bytes=0;
        for(String text:input) {
            validate(text);
            int size=text.getBytes(StandardCharsets.UTF_8).length;
            if(!current.isEmpty() && (current.size()==16 || bytes+size>3072)) {
                result.add(current); current=new ArrayList<>(); bytes=0;
            }
            current.add(text); bytes+=size;
        }
        if(!current.isEmpty())result.add(current);
        return result;
    }

    static byte[] request(List<String> input) {
        if(input==null || input.isEmpty() || input.size()>16)throw invalid();
        ObjectNode body=JSON.createObjectNode().put("model",MODEL).put("encoding_format","float");
        ArrayNode values=body.putArray("input"); int bytes=0;
        for(String text:input) {validate(text);bytes+=text.getBytes(StandardCharsets.UTF_8).length;values.add(text);}
        // shortcut: conservative byte bound for this small English pilot; tokenizer-aware packing belongs to a larger corpus.
        if(bytes>3072)throw invalid();
        try{return JSON.writeValueAsBytes(body);}catch(IOException failure){throw invalid();}
    }

    static Batch fetch(List<String> input,String key) throws IOException {
        if(key==null || key.trim().isEmpty() || !key.matches("[\\x21-\\x7e]+"))throw invalid();
        byte[] outgoing=request(input);
        FaqRequestBudget budget=new FaqRequestBudget(35000);
        HttpURLConnection connection=(HttpURLConnection)new URL(ENDPOINT).openConnection();
        connection.setRequestMethod("POST"); connection.setDoOutput(true);
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(budget.socketTimeoutMillis(3000));
        connection.setReadTimeout(budget.socketTimeoutMillis(30000));
        connection.setFixedLengthStreamingMode(outgoing.length);
        connection.setRequestProperty("Content-Type","application/json");
        connection.setRequestProperty("Authorization","Bearer "+key);
        try {
            try(OutputStream body=connection.getOutputStream()){body.write(outgoing);}
            budget.checkActive();
            int status=connection.getResponseCode();
            if(status!=200)throw new IOException("Embedding pilot HTTP status "+status);
            ByteArrayOutputStream body=new ByteArrayOutputStream();
            try(InputStream stream=connection.getInputStream()) {
                byte[] buffer=new byte[4096];
                while(true) {
                    connection.setReadTimeout(budget.socketTimeoutMillis(30000));
                    int count=stream.read(buffer); budget.checkActive();
                    if(count<0)break;
                    if(body.size()+count>1048576)throw invalid();
                    body.write(buffer,0,count);
                }
            }
            return parse(body.toByteArray(),input.size());
        }finally{connection.disconnect();}
    }

    static Batch parse(byte[] bytes,int expected) {
        try {
            JsonNode body=JSON.readTree(bytes);
            if(bytes.length>1048576 || body==null || !MODEL.equals(body.path("model").asText())
                    || !body.path("data").isArray() || body.path("data").size()!=expected
                    || !body.path("usage").path("prompt_tokens").isIntegralNumber()
                    || !body.path("usage").path("total_tokens").isIntegralNumber()
                    || !body.path("usage").path("prompt_tokens").canConvertToLong()
                    || !body.path("usage").path("total_tokens").canConvertToLong())throw invalid();
            long tokens=body.path("usage").path("prompt_tokens").longValue();
            if(tokens<=0 || tokens>40000 || body.path("usage").path("total_tokens").longValue()!=tokens)throw invalid();
            double[][] vectors=new double[expected][];
            for(JsonNode item:body.path("data")) {
                JsonNode index=item.path("index"), vector=item.path("embedding");
                if(!index.isIntegralNumber() || !index.canConvertToInt() || index.intValue()<0 || index.intValue()>=expected
                        || vectors[index.intValue()]!=null || !vector.isArray() || vector.size()!=DIMENSIONS)throw invalid();
                double[] values=new double[DIMENSIONS]; double norm=0;
                for(int d=0;d<DIMENSIONS;d++) {
                    if(!vector.get(d).isNumber() || !Double.isFinite(vector.get(d).doubleValue()))throw invalid();
                    values[d]=vector.get(d).doubleValue(); norm+=values[d]*values[d];
                }
                if(!Double.isFinite(norm) || norm==0)throw invalid();
                vectors[index.intValue()]=values;
            }
            return new Batch(vectors,tokens);
        }catch(IOException failure){throw invalid();}
    }

    static double cosine(double[] left,double[] right) {
        if(left.length!=DIMENSIONS || right.length!=DIMENSIONS)throw invalid();
        double dot=0,leftNorm=0,rightNorm=0;
        for(int d=0;d<DIMENSIONS;d++){dot+=left[d]*right[d];leftNorm+=left[d]*left[d];rightNorm+=right[d]*right[d];}
        // shortcut: pilot vectors have near-unit norms; stabilize this formula before accepting extreme-magnitude vectors.
        return dot/Math.sqrt(leftNorm*rightNorm);
    }
    private static void validate(String text) {
        if(text==null || text.replaceAll("(?U)\\s","").isEmpty() || text.getBytes(StandardCharsets.UTF_8).length>3072)throw invalid();
    }
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Invalid embedding pilot data.");}
    static final class Batch {
        final double[][] vectors;
        final long tokens;
        Batch(double[][] vectors,long tokens){this.vectors=vectors;this.tokens=tokens;}
    }
}
