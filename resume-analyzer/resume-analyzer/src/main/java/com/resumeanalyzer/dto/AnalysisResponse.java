package com.resumeanalyzer.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Full structured ATS analysis response.
 * Designed to provide all data needed by the future Stitch Results page.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AnalysisResponse {

    private Long id;
    private Long resumeId;
    private String resumeFileName;
    private Long jobDescriptionId;
    private String jobTitle;
    private String companyName;

    private Integer atsScore;

    private ScoreBreakdown scoreBreakdown;

    private List<String> matchedSkills;
    private List<String> missingSkills;
    private List<String> optionalSkills;

    private List<String> matchedKeywords;
    private List<String> missingKeywords;

    private KeywordStuffingInfo keywordStuffing;

    /**
     * Per-keyword frequency and coverage telemetry, one entry per keyword drawn from
     * the job description (plus any keyword flagged for repetition in the resume).
     *
     * This is the raw data behind the keyword-frequency visualisation: how often the
     * job description itself uses a term versus how often the resume does, whether the
     * term was matched, and whether the repetition detector flagged it.
     */
    private List<KeywordCoverage> keywordCoverage;

    private List<StructureCheck> structure;

    /**
     * Actionable recommendations derived deterministically from the findings above
     * (missing skills, missing keywords, structure warnings, repetition, low
     * sub-scores). No AI or LLM is involved and no resume content is invented.
     */
    private List<Recommendation> recommendations;

    /**
     * LLM-authored suggestions, present only after the user has explicitly requested
     * them AND a provider is configured. Kept in a separate field so the deterministic
     * advice above is never misrepresented as AI-generated, and so the score is
     * unaffected by anything the model returns.
     */
    private List<Recommendation> aiRecommendations;

    private LocalDateTime aiGeneratedAt;

    /** Hostname of the configured provider, for display. Never a key. */
    private String aiProvider;

    private String aiModel;

    private LocalDateTime createdAt;

    // -----------------------------------------------------------------------
    // Nested DTOs
    // -----------------------------------------------------------------------

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ScoreBreakdown {
        private Integer skillMatch;
        private Integer keywordMatch;
        private Integer relevance;
        private Integer structure;
        private Integer formatting;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class KeywordStuffingInfo {
        private String risk;
        private List<FlaggedKeyword> flaggedKeywords;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class FlaggedKeyword {
        private String keyword;
        private Integer frequency;
        private String reason;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StructureCheck {
        private String section;
        private String status; // "PASSED" or "WARNING"
        private String message;
    }

    /**
     * One row of the keyword-frequency table.
     *
     * `jobDescriptionCount` and `resumeCount` are whole-word occurrence counts, so a
     * legitimate repeated technical term (e.g. "java" in a Java developer's resume) is
     * reported as a count rather than a penalty. The only judgement applied is the
     * separate `flagged` flag, which is set by the repetition detector's documented
     * density thresholds.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class KeywordCoverage {
        private String keyword;
        /** Whole-word occurrences in the job description text. */
        private Integer jobDescriptionCount;
        /** Whole-word occurrences in the extracted resume text. */
        private Integer resumeCount;
        /** True when the resume contains the keyword at least once. */
        private Boolean matched;
        /** True when the repetition detector flagged this keyword. */
        private Boolean flagged;
        private String flagReason;
        /** True when the keyword came from the job description (rather than only the resume). */
        private Boolean inJobDescription;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Recommendation {
        private String category;   // Skills / Keywords / Structure / Repetition / Formatting
        private String priority;   // HIGH / MEDIUM / LOW
        private String title;
        private String detail;
        private List<String> relatedItems;
        /**
         * "RULE" for the deterministic generator, "AI" for LLM-authored suggestions.
         * The UI must never present rule-based output as AI-generated.
         */
        private String source;
    }
}
