package com.example.springboot.faq;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.util.Arrays;
import java.util.Collections;
import com.fasterxml.jackson.databind.node.ObjectNode;
import static com.example.springboot.faq.FaqStep6AcceptanceReportTest.*;

/** A separately authorized regression pass; never replaces the historical run or claims blindness. */
class FaqDeliveryAcceptanceIT extends FaqRecallLiveIT {
    FaqDeliveryAcceptanceIT() throws Exception {
        sampleFile="docs/faq-evaluation/holdout.json"; sampleSha=HOLDOUT_SHA;
        runName="delivery-final-20261122"; budgetVariable="FAQ_DELIVERY_ACCEPTANCE_BUDGET";
        mainCount=40; maxGenerations=40; maxEmbeddings=43; diagnosticIdsToRun=Collections.emptySet();
        for (String file:Arrays.asList("llm/LlmClient.java","retrieval/SemanticFaqRetriever.java","service/FaqService.java"))
            expectedSources.put(file,sha(root().resolve("springboot/src/main/java/com/example/springboot/faq/"+file)));
    }
    @Override protected void configurePolicy(ObjectNode policy) {
        policy.put("minimumPositiveExpectedSourceAnswers",24).put("maximumNegativeAnswered",0)
                .put("maximumWrongSourceAnswers",0).put("maximumUnavailableCases",0)
                .put("requiresAllGeneratedFactsSupported",true).put("previouslyExposedRegressionSet",true)
                .put("isIndependentAcceptance",false).put("regressionCount",40);
    }
    @Override @Test @EnabledIfEnvironmentVariable(named="FAQ_DELIVERY_ACCEPTANCE_VERIFY", matches="true")
    void oneReadOnlyPassWithoutRetriesOrRetuning() throws Exception {
        super.oneReadOnlyPassWithoutRetriesOrRetuning();
    }
}
