package com.example.springboot.faq.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;

/** Immutable, backend-only configuration. Never include credentials in toString or errors. */
@Component
public class FaqLlmProperties {
    private final boolean enabled;
    private final URI endpoint;
    private final String apiKey;
    private final String model;
    private final String outputTokenParameter;

    @Autowired
    public FaqLlmProperties(Environment environment) {
        this(environment, environment.acceptsProfiles(Profiles.of("faq-deepseek")) ? "faq.deepseek." : "faq.llm.");
    }

    private FaqLlmProperties(Environment environment, String prefix) {
        this(environment.getProperty(prefix + "enabled", Boolean.class, false),
                environment.getProperty(prefix + "base-url", ""),
                environment.getProperty(prefix + "api-key", ""),
                environment.getProperty(prefix + "model", ""),
                environment.getProperty(prefix + "output-token-parameter", "max_completion_tokens"));
    }

    public FaqLlmProperties(boolean enabled, String baseUrl, String apiKey, String model, String parameter) {
        this.enabled = enabled;
        this.apiKey = apiKey;
        this.model = model;
        this.outputTokenParameter = parameter;
        this.endpoint = enabled ? validate(baseUrl, apiKey, model, parameter) : null;
    }

    private URI validate(String baseUrl, String key, String model, String parameter) {
        try {
            URI uri = new URI(baseUrl == null ? "" : baseUrl);
            String path = uri.getRawPath() == null ? "" : uri.getRawPath();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || uri.getPort() == 0 || uri.getPort() > 65535
                    || !path.matches("/[A-Za-z0-9_./~-]*|") || path.matches(".*(^|/)\\.\\.?(/|$).*")
                    || key == null || !key.matches("[!-~]+")
                    || model == null || !model.matches("[A-Za-z0-9][A-Za-z0-9._:/-]{0,199}")
                    || !("max_completion_tokens".equals(parameter) || "max_tokens".equals(parameter))) {
                throw invalid();
            }
            return new URI(baseUrl.replaceAll("/+$", "") + "/chat/completions");
        } catch (URISyntaxException error) {
            throw invalid();
        }
    }

    private IllegalArgumentException invalid() {
        return new IllegalArgumentException("FAQ LLM configuration is incomplete or invalid. Check enabled, HTTPS base URL, API key, model and output token parameter.");
    }

    public boolean isEnabled() { return enabled; }
    public URI getEndpoint() { return endpoint; }
    public String getApiKey() { return apiKey; }
    public String getModel() { return model; }
    public String getOutputTokenParameter() { return outputTokenParameter; }
}
