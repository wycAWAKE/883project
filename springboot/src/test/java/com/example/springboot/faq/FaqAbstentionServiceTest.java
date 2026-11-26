package com.example.springboot.faq;
import com.example.springboot.faq.config.FaqLlmProperties;
import com.example.springboot.faq.entity.FaqEntry;
import com.example.springboot.faq.llm.LlmClient;
import com.example.springboot.faq.mapper.FaqMapper;
import com.example.springboot.faq.retrieval.TfIdfRetriever;
import com.example.springboot.faq.service.FaqService;
import com.example.springboot.faq.support.FaqRequestBudget;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.http.MediaType;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
class FaqAbstentionServiceTest {
 @Test void explicitRefusalReplacesStoredFallbackAndReturnsNoSources() {
  LlmClient client=new LlmClient(new FaqLlmProperties(true,"https://fixture.example/v1","fixture-key","fixture","max_tokens"),new ObjectMapper(),new RestTemplateBuilder().additionalCustomizers(template->{
   MockRestServiceServer server=MockRestServiceServer.bindTo(template).build();server.expect(anything()).andRespond(withSuccess("{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"role\":\"assistant\",\"content\":\"{\\\"action\\\":\\\"insufficient_evidence\\\",\\\"answer\\\":\\\"\\\",\\\"citedFaqIds\\\":[]}\"}}]}",MediaType.APPLICATION_JSON));
  }));
  FaqEntry row=new FaqEntry();row.setId(1L);row.setQuestion("card fee");row.setAnswer("Only an old replacement guide.");row.setSourceTitle("Guide");row.setSourceUrl("https://nisc.ccnu.edu.cn/card");row.setEnabled(true);
  FaqMapper mapper=mock(FaqMapper.class);when(mapper.findEnabled()).thenReturn(Collections.singletonList(row));
  FaqRequestBudget budget=new FaqRequestBudget(35000);
  com.example.springboot.faq.dto.FaqAnswerResponse result=new FaqService(mapper,new TfIdfRetriever(),.35,client).ask("card fee",budget);
  assertEquals("no_match",result.getMode());assertNull(result.getAnswer());assertTrue(result.getSources().isEmpty());
  assertEquals("no_match",budget.getFallback().getMode());
 }
}
