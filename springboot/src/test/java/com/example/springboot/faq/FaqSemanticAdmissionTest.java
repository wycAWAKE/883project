package com.example.springboot.faq;
import com.example.springboot.faq.config.FaqLlmProperties;
import com.example.springboot.faq.entity.FaqEntry;
import com.example.springboot.faq.llm.LlmClient;
import com.example.springboot.faq.mapper.FaqMapper;
import com.example.springboot.faq.retrieval.*;
import com.example.springboot.faq.service.FaqService;
import com.example.springboot.faq.support.FaqRequestBudget;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class FaqSemanticAdmissionTest {
    final EmbeddingClient client=mock(EmbeddingClient.class);
    final FaqMapper mapper=mock(FaqMapper.class);
    final LlmClient llm=new LlmClient(new FaqLlmProperties(false,"","","","max_tokens"),new ObjectMapper(),new RestTemplateBuilder());
    FaqService service(double score) {
        when(client.isEnabled()).thenReturn(true);
        when(client.embed(anyList(),any())).thenAnswer(i->{List<String> texts=i.getArgument(0);double[][] v=new double[texts.size()][];
            for(int n=0;n<v.length;n++){v[n]=new double[1024];if(texts.get(n).equals("calendar "))v[n][0]=1;else{v[n][0]=score;v[n][1]=Math.sqrt(1-score*score);}}return v;});
        FaqEntry row=new FaqEntry();row.setId(1L);row.setQuestion("calendar");row.setAnswer("Published calendar.");row.setEnabled(true);row.setSourceTitle("Calendar");row.setSourceUrl("https://jwc.ccnu.edu.cn/calendar");
        when(mapper.findEnabled()).thenReturn(Collections.singletonList(row));
        return new FaqService(mapper,new TfIdfRetriever(),.35,llm,new SemanticFaqRetriever(client,true,.73));
    }
    @Test void semanticRecallDoesNotRequireLexicalOverlap(){assertEquals(1L,service(1).ask("semester timetable").getFaqId());}
    @Test void healthyRejectionNeverResurrectsLexicalAnswer(){FaqService s=service(.72);assertEquals("no_match",s.ask("calendar").getMode());}
    @Test void thresholdEqualityAccepts(){assertEquals(1L,service(.73).ask("calendar").getFaqId());}
    @Test void providerFailureFallsBackToCurrentLexicalEvidence(){FaqService s=service(1);doThrow(new IllegalArgumentException()).when(client).embed(anyList(),any());assertEquals(1L,s.ask("calendar").getFaqId());assertEquals("no_match",s.ask("timetable").getMode());}
    @Test void healthyEmptyResultAndUnavailableAreDifferent(){service(.72);SemanticFaqRetriever r=new SemanticFaqRetriever(client,true,.73);assertTrue(r.search("calendar",mapper.findEnabled(),new FaqRequestBudget(35000)).isAvailable());assertTrue(r.search("calendar",mapper.findEnabled(),new FaqRequestBudget(35000)).getMatches().isEmpty());}
    @Test void invalidThresholdRejected(){assertThrows(IllegalArgumentException.class,()->new SemanticFaqRetriever(client,true,Double.NaN));}
    @Test void parentTimeoutCannotReturnUnqualifiedLexicalAnswer(){
        FaqService s=service(1);
        doAnswer(i->{try{Thread.sleep(1500);}catch(InterruptedException e){Thread.currentThread().interrupt();}return new double[][]{new double[1024]};}).when(client).embed(anyList(),any());
        com.example.springboot.faq.service.FaqRequestRunner runner=new com.example.springboot.faq.service.FaqRequestRunner(s,200);
        try{assertEquals("no_match",runner.ask("calendar").getMode());}finally{runner.close();}
    }
    @Test void generationEnablementCheckCannotPublishStrictStoredFallback(){
        service(1);
        LlmClient slow=new LlmClient(new FaqLlmProperties(false,"","","","max_tokens"),new ObjectMapper(),new RestTemplateBuilder()){
            @Override public boolean isEnabled(){try{Thread.sleep(1500);}catch(InterruptedException e){Thread.currentThread().interrupt();}return true;}
        };
        FaqService s=new FaqService(mapper,new TfIdfRetriever(),.35,slow,new SemanticFaqRetriever(client,true,.73));
        com.example.springboot.faq.service.FaqRequestRunner runner=new com.example.springboot.faq.service.FaqRequestRunner(s,200);
        try{assertEquals("no_match",runner.ask("calendar").getMode());}finally{runner.close();}
    }
}
