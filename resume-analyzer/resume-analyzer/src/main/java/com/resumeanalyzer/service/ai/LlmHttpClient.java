package com.resumeanalyzer.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.resumeanalyzer.config.LlmProperties;
import com.resumeanalyzer.exception.AiGenerationException;
import com.resumeanalyzer.exception.AiUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * The only place the application talks to an LLM provider.
 *
 * Uses the JDK HTTP client directly: no extra dependency, and connect/read timeouts
 * are explicit so a hanging provider cannot hang a request thread indefinitely.
 *
 * Security notes:
 *  - The API key is read from configuration, placed in the Authorization header and
 *    never logged, never returned and never sent to the browser.
 *  - Prompt and response bodies are NOT logged (they contain the user's resume text).
 *    Only the HTTP status and error category are logged.
 */
@Component
public class LlmHttpClient {

    private static final Logger log = LoggerFactory.getLogger(LlmHttpClient.class);

    private final LlmProperties properties;
    private final ObjectMapper objectMapper;

    public LlmHttpClient(LlmProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /** Raw assistant text returned by the provider. */
    public String complete(String systemPrompt, String userPrompt) {
        if (!properties.isUsable()) {
            throw new AiUnavailableException(properties.unavailableReason());
        }

        String requestBody = buildRequestBody(systemPrompt, userPrompt);
        Duration timeout = Duration.ofMillis(Math.max(500, properties.getTimeoutMs()));

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        HttpRequest request = HttpRequest.newBuilder(URI.create(properties.endpoint()))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + properties.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiGenerationException("The AI request was interrupted.", e);
        } catch (InterruptedIOException e) {
            throw new AiGenerationException("The AI provider did not respond within "
                    + properties.getTimeoutMs() + " ms.", e);
        } catch (IOException e) {
            // Connection refused, DNS failure, TLS failure, timeout.
            throw new AiGenerationException("The AI provider could not be reached: "
                    + e.getClass().getSimpleName() + ".", e);
        }

        int status = response.statusCode();
        if (status == 401 || status == 403) {
            log.warn("LLM provider rejected the credentials (HTTP {}).", status);
            throw new AiGenerationException("The AI provider rejected the configured credentials "
                    + "(HTTP " + status + ").", null);
        }
        if (status == 429) {
            log.warn("LLM provider rate-limited the request (HTTP 429).");
            throw new AiGenerationException("The AI provider is rate-limiting requests. Try again shortly.", null);
        }
        if (status >= 500) {
            log.warn("LLM provider returned a server error (HTTP {}).", status);
            throw new AiGenerationException("The AI provider returned an error (HTTP " + status + ").", null);
        }
        if (status < 200 || status >= 300) {
            log.warn("LLM provider returned an unexpected status (HTTP {}).", status);
            throw new AiGenerationException("The AI provider returned an unexpected response (HTTP "
                    + status + ").", null);
        }

        return extractContent(response.body());
    }

    private String buildRequestBody(String systemPrompt, String userPrompt) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", properties.getModel());
        root.put("temperature", 0.2);

        ArrayNode messages = root.putArray("messages");
        ObjectNode system = messages.addObject();
        system.put("role", "system");
        system.put("content", systemPrompt);
        ObjectNode user = messages.addObject();
        user.put("role", "user");
        user.put("content", userPrompt);

        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new AiGenerationException("Could not build the AI request payload.", e);
        }
    }

    /**
     * Pull the assistant text out of an OpenAI-compatible response.
     * Accepts `choices[0].message.content` (chat) and `choices[0].text` (legacy).
     */
    private String extractContent(String body) {
        if (body == null || body.isBlank()) {
            throw new AiGenerationException("The AI provider returned an empty response.", null);
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                throw new AiGenerationException("The AI provider response contained no choices.", null);
            }
            JsonNode first = choices.get(0);
            JsonNode content = first.path("message").path("content");
            if (!content.isTextual() || content.asText().isBlank()) {
                content = first.path("text");
            }
            if (!content.isTextual() || content.asText().isBlank()) {
                throw new AiGenerationException("The AI provider returned no usable text.", null);
            }
            return content.asText();
        } catch (AiGenerationException e) {
            throw e;
        } catch (Exception e) {
            throw new AiGenerationException("The AI provider response could not be parsed.", e);
        }
    }
}
