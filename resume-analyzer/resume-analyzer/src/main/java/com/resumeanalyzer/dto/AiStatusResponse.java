package com.resumeanalyzer.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tells the client whether the optional LLM feature can be used.
 *
 * Contains no secret: the API key and full base URL are never exposed, only the
 * provider hostname and the model name, which are configuration labels rather than
 * credentials. When `enabled` is false, `reason` explains what an operator must set.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AiStatusResponse {

    private Boolean enabled;

    /** Actionable explanation when disabled. Absent when enabled. */
    private String reason;

    /** Provider hostname (e.g. api.openai.com). Absent when disabled. */
    private String provider;

    private String model;

    private Integer maxRecommendations;

    /**
     * Shown next to the AI section so a reader always knows the origin of the text.
     * Rule-based recommendations are never described as AI-generated.
     */
    private String disclaimer;
}
