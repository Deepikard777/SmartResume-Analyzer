package com.resumeanalyzer.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Configuration for the optional LLM-backed recommendation feature.
 *
 * The feature is OFF unless an operator supplies credentials, and every value comes
 * from the environment. There is no default API key and no API key ever reaches the
 * browser: the frontend only learns whether the feature is enabled (see
 * GET /api/ai/status), never the key, the base URL or the model credentials.
 *
 * The transport is the OpenAI-compatible /chat/completions shape, which is also
 * served by Azure OpenAI, Groq, Together, OpenRouter, vLLM, Ollama and LM Studio.
 * Pointing `app.llm.base-url` at a different provider is the only change needed.
 */
@Component
public class LlmProperties {

    /** Master switch. When false every AI endpoint answers 503 and nothing is sent anywhere. */
    private final boolean enabled;

    /** Base URL WITHOUT the trailing /chat/completions path. */
    private final String baseUrl;

    /** Secret. Read from the environment only; never logged and never returned to a client. */
    private final String apiKey;

    private final String model;

    /** Connect + read timeout for the provider call, in milliseconds. */
    private final int timeoutMs;

    /** Upper bound on characters of resume text sent to the provider. */
    private final int maxResumeChars;

    /** Upper bound on characters of job-description text sent to the provider. */
    private final int maxJobDescriptionChars;

    /** Hard cap on how many recommendations are accepted from a single response. */
    private final int maxRecommendations;

    public LlmProperties(
            @Value("${app.llm.enabled:false}") boolean enabled,
            @Value("${app.llm.base-url:}") String baseUrl,
            @Value("${app.llm.api-key:}") String apiKey,
            @Value("${app.llm.model:gpt-4o-mini}") String model,
            @Value("${app.llm.timeout-ms:8000}") int timeoutMs,
            @Value("${app.llm.max-resume-chars:6000}") int maxResumeChars,
            @Value("${app.llm.max-job-description-chars:4000}") int maxJobDescriptionChars,
            @Value("${app.llm.max-recommendations:6}") int maxRecommendations) {
        this.enabled = enabled;
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.apiKey = apiKey;
        this.model = model;
        this.timeoutMs = timeoutMs;
        this.maxResumeChars = maxResumeChars;
        this.maxJobDescriptionChars = maxJobDescriptionChars;
        this.maxRecommendations = maxRecommendations;
    }

    /**
     * The feature is usable only when it is switched on AND fully configured. A
     * half-configured deployment behaves exactly like a disabled one, so a missing
     * environment variable can never produce a partially working, confusing state.
     */
    public boolean isUsable() {
        return enabled && isNotBlank(baseUrl) && isNotBlank(apiKey) && isNotBlank(model);
    }

    /**
     * A non-sensitive explanation of why the feature is unavailable, safe to show in a
     * UI tooltip. It never includes the key itself.
     */
    public String unavailableReason() {
        if (!enabled) {
            return "The AI recommendation feature is switched off on this server "
                    + "(app.llm.enabled=false).";
        }
        if (isBlank(baseUrl)) {
            return "No LLM base URL is configured (app.llm.base-url / LLM_BASE_URL).";
        }
        if (isBlank(apiKey)) {
            return "No LLM API key is configured (app.llm.api-key / LLM_API_KEY).";
        }
        if (isBlank(model)) {
            return "No LLM model is configured (app.llm.model / LLM_MODEL).";
        }
        return "The AI recommendation feature is unavailable.";
    }

    public String endpoint() {
        return baseUrl + "/chat/completions";
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    /** Only ever used to build the Authorization header. Must not be logged or serialised. */
    public String getApiKey() {
        return apiKey;
    }

    public String getModel() {
        return model;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public int getMaxResumeChars() {
        return maxResumeChars;
    }

    public int getMaxJobDescriptionChars() {
        return maxJobDescriptionChars;
    }

    public int getMaxRecommendations() {
        return maxRecommendations;
    }

    private static String trimTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean isNotBlank(String value) {
        return !isBlank(value);
    }
}
