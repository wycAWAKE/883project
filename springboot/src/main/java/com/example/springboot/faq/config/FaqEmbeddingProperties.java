package com.example.springboot.faq.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Selected provider only; no fallback to chat credentials or paid model aliases. */
@Component
public class FaqEmbeddingProperties {
    private final boolean enabled;
    private final String apiKey;

    @Autowired
    public FaqEmbeddingProperties(Environment environment) {
        this(environment.getProperty("faq.embedding.enabled", Boolean.class, false),
                environment.getProperty("SILICONFLOW_API_KEY", ""));
    }

    public FaqEmbeddingProperties(boolean enabled, String apiKey) {
        if (enabled && (apiKey == null || !apiKey.matches("[!-~]+"))) {
            throw new IllegalArgumentException("FAQ embedding requires a valid private SILICONFLOW_API_KEY.");
        }
        this.enabled = enabled;
        this.apiKey = apiKey;
    }

    public boolean isEnabled() { return enabled; }
    public String getApiKey() { return apiKey; }
}
