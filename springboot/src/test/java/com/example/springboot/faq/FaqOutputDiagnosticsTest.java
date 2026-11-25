package com.example.springboot.faq;

import com.example.springboot.faq.config.FaqLlmProperties;
import com.example.springboot.faq.entity.FaqEntry;
import com.example.springboot.faq.llm.LlmClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FaqOutputDiagnosticsTest {
    @ParameterizedTest
    @CsvSource({"json,CONTENT_JSON", "fields,ANSWER_SCHEMA", "refusal,ANSWER_SCHEMA",
            "link,ANSWER_LINK", "id,CITATION_ID", "marker,CITATION_MARKER", "envelope,COMPLETION_SCHEMA"})
    void identifiesValidationBoundaryWithoutExposingProviderText(String fixture, String reason) throws Exception {
        ObjectMapper json=new ObjectMapper();
        MockRestServiceServer[] server=new MockRestServiceServer[1];
        LlmClient client=new LlmClient(new FaqLlmProperties(true,"https://model.example/v1","private-test-key",
                "fixture-model","max_tokens"),json,new RestTemplateBuilder().additionalCustomizers(
                        http->server[0]=MockRestServiceServer.bindTo(http).build()));
        ObjectNode answer=json.createObjectNode().put("answer","Contact support. [FAQ:7]");
        answer.putArray("citedFaqIds").add(7);
        if ("fields".equals(fixture)) answer.put("unexpected","private-provider-text");
        if ("refusal".equals(fixture)) answer.put("action","insufficient_evidence");
        if ("link".equals(fixture)) answer.put("answer","See https://private-provider-text.example. [FAQ:7]");
        if ("id".equals(fixture)) answer.withArray("citedFaqIds").removeAll().add(99);
        if ("marker".equals(fixture)) answer.put("answer","Contact support. [FAQ:99]");
        ObjectNode completion=json.createObjectNode();
        ObjectNode choice=completion.putArray("choices").addObject().put("finish_reason","stop");
        choice.putObject("message").put("role","assistant").put("content",
                "json".equals(fixture)?"private-provider-text broken JSON":answer.toString());
        if ("envelope".equals(fixture)) choice.put("finish_reason","length");
        server[0].expect(requestTo("https://model.example/v1/chat/completions"))
                .andRespond(withSuccess(completion.toString(),MediaType.APPLICATION_JSON));
        FaqEntry entry=new FaqEntry(); entry.setId(7L); entry.setQuestion("Who can help?");
        entry.setAnswer("Contact support."); entry.setEnabled(true);
        LlmClient.LlmException error=assertThrows(LlmClient.LlmException.class,
                ()->client.generate("Who can help?", Collections.singletonList(entry)));
        assertEquals(LlmClient.Type.INVALID_RESPONSE,error.getType());
        assertEquals(reason,error.getValidationReason().name());
        assertNull(error.getCause());
        assertFalse(error.toString().contains("private-provider-text"));
        assertFalse(error.toString().contains("private-test-key"));
        server[0].verify();
    }
}
