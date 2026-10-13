package com.resumeanalyzer.service.analysis;

import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Keyword Stuffing Detector — deterministic heuristic.
 *
 * IMPORTANT: These thresholds are project-defined heuristics for educational
 * purposes. They do NOT represent official ATS industry standards.
 *
 * Algorithm:
 * 1. For each keyword that appears in the JD, count its occurrences in the resume.
 * 2. Compute resume word count for relative frequency calculation.
 * 3. Apply two-tier flagging rules:
 *
 *    HIGH STUFFING — keyword density in resume ≥ HIGH_DENSITY_THRESHOLD (2.5%)
 *                    AND frequency ≥ HIGH_ABSOLUTE_THRESHOLD (8 times)
 *
 *    MEDIUM STUFFING — keyword frequency ≥ MEDIUM_ABSOLUTE_THRESHOLD (5 times)
 *                      AND keyword density ≥ MEDIUM_DENSITY_THRESHOLD (1.5%)
 *
 *    LOW (normal) — keyword appears legitimately
 *
 * Overall risk = maximum risk across all flagged keywords.
 * If no keywords flagged → LOW.
 */
@Component
public class KeywordStuffingDetector {

    // --- Thresholds (documented, not magic numbers) ---

    /** Keyword density (occurrences / total words) for HIGH risk, as percentage */
    private static final double HIGH_DENSITY_THRESHOLD   = 0.025; // 2.5%

    /** Absolute occurrence count for HIGH risk */
    private static final int    HIGH_ABSOLUTE_THRESHOLD  = 8;

    /** Keyword density for MEDIUM risk */
    private static final double MEDIUM_DENSITY_THRESHOLD = 0.015; // 1.5%

    /** Absolute occurrence count for MEDIUM risk */
    private static final int    MEDIUM_ABSOLUTE_THRESHOLD = 5;

    public enum StuffingRisk { LOW, MEDIUM, HIGH }

    /**
     * A single flagged keyword entry.
     */
    public record FlaggedEntry(String keyword, int frequency, String reason) {}

    /**
     * Full stuffing detection result.
     */
    public record StuffingResult(
            StuffingRisk overallRisk,
            List<FlaggedEntry> flaggedKeywords
    ) {}

    /**
     * Analyze the resume text for keyword stuffing relative to JD keywords.
     *
     * @param resumeText  the full extracted resume text
     * @param jdKeywords  meaningful keywords extracted from the JD
     * @param keywordMatcher  the matcher (reused for counting)
     * @return StuffingResult with overall risk and flagged keyword details
     */
    public StuffingResult detect(String resumeText,
                                 List<String> jdKeywords,
                                 KeywordMatcher keywordMatcher) {
        if (resumeText == null || resumeText.isBlank() || jdKeywords == null || jdKeywords.isEmpty()) {
            return new StuffingResult(StuffingRisk.LOW, List.of());
        }

        int totalWords = countWords(resumeText);
        if (totalWords == 0) {
            return new StuffingResult(StuffingRisk.LOW, List.of());
        }

        String lowerResume = resumeText.toLowerCase();
        List<FlaggedEntry> flagged = new ArrayList<>();
        StuffingRisk overallRisk = StuffingRisk.LOW;

        for (String keyword : jdKeywords) {
            int freq = keywordMatcher.countOccurrences(lowerResume, keyword);
            if (freq == 0) continue;

            double density = (double) freq / totalWords;
            StuffingRisk keywordRisk = StuffingRisk.LOW;
            String reason = null;

            if (freq >= HIGH_ABSOLUTE_THRESHOLD && density >= HIGH_DENSITY_THRESHOLD) {
                keywordRisk = StuffingRisk.HIGH;
                reason = String.format(
                        "Appears %d times (%.1f%% of resume). HIGH stuffing risk — unusually high repetition.",
                        freq, density * 100);
            } else if (freq >= MEDIUM_ABSOLUTE_THRESHOLD && density >= MEDIUM_DENSITY_THRESHOLD) {
                keywordRisk = StuffingRisk.MEDIUM;
                reason = String.format(
                        "Appears %d times (%.1f%% of resume). MEDIUM stuffing risk — elevated repetition.",
                        freq, density * 100);
            }

            if (keywordRisk != StuffingRisk.LOW) {
                flagged.add(new FlaggedEntry(keyword, freq, reason));
                if (keywordRisk.ordinal() > overallRisk.ordinal()) {
                    overallRisk = keywordRisk;
                }
            }
        }

        return new StuffingResult(overallRisk, Collections.unmodifiableList(flagged));
    }

    /** Rough word count (split on whitespace). */
    private int countWords(String text) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) return 0;
        return trimmed.split("\\s+").length;
    }
}
