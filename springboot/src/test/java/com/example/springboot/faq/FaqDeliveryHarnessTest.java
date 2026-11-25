package com.example.springboot.faq;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.platform.commons.support.AnnotationSupport;
import org.junit.platform.commons.support.HierarchyTraversalMode;
import java.lang.reflect.Method;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FaqDeliveryHarnessTest {
    @Test void deliveryDiscoversOnlyItsOwnExplicitlyGatedLiveTest() {
        List<Method> tests=AnnotationSupport.findAnnotatedMethods(FaqDeliveryAcceptanceIT.class,
                Test.class,HierarchyTraversalMode.TOP_DOWN);
        assertEquals(1,tests.size(),"Inherited recall tests must not provide another live entry point");
        EnabledIfEnvironmentVariable gate=tests.get(0).getAnnotation(EnabledIfEnvironmentVariable.class);
        assertNotNull(gate); assertEquals("FAQ_DELIVERY_ACCEPTANCE_VERIFY",gate.named());
        assertEquals("true",gate.matches());
    }
}
