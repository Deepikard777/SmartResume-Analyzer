package com.resumeanalyzer.controller;

import com.resumeanalyzer.config.LlmProperties;
import com.resumeanalyzer.dto.AiStatusResponse;
import com.resumeanalyzer.service.ai.AiRecommendationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only capability probe for the optional LLM feature.
 *
 * Requires authentication like every other non-credential endpoint. It reports only
 * whether the feature is usable and, if not, what has to be configured — never the
 * API key or the full provider URL.
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private static final String DISCLAIMER =
            "AI-generated suggestions. They are advisory only and were not used to compute the score. "
                    + "Always check that anything you add is genuinely yours.";

    private final AiRecommendationService aiRecommendationService;
    private final LlmProperties properties;

    public AiController(AiRecommendationService aiRecommendationService, LlmProperties properties) {
        this.aiRecommendationService = aiRecommendationService;
        this.properties = properties;
    }

    @GetMapping("/status")
    public ResponseEntity<AiStatusResponse> status() {
        boolean usable = aiRecommendationService.isUsable();

        AiStatusResponse response = AiStatusResponse.builder()
                .enabled(usable)
                .reason(usable ? null : aiRecommendationService.unavailableReason())
                .provider(usable ? aiRecommendationService.providerLabel() : null)
                .model(usable ? aiRecommendationService.modelLabel() : null)
                .maxRecommendations(properties.getMaxRecommendations())
                .disclaimer(usable ? DISCLAIMER : null)
                .build();

        return ResponseEntity.ok(response);
    }
}
