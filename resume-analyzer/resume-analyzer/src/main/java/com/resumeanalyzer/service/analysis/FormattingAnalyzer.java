package com.resumeanalyzer.service.analysis;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Formatting Analyzer — deterministic text quality assessment.
 *
 * Checks extracted text for formatting signals that suggest a clean, readable resume
 * vs one that has extraction artifacts, excessive repetition, or structural problems.
 *
 * NOTE: This analyzes extracted TEXT quality, not visual PDF layout.
 * It cannot assess fonts, margins, colors, or visual design.
 *
 * Checks performed:
 *  1. Minimum content length (very short = likely extraction failed or empty resume)
 *  2. Excessive whitespace ratio (too many blank lines vs content)
 *  3. Repeated consecutive characters (e.g., "======" or "----" in excess)
 *  4. Extremely long single lines (suggests no line breaks = possible parse artifact)
 *  5. Non-printable or garbled character ratio (encoding issues)
 *
 * Each check contributes to a 0–100 score.
 * Deductions are capped so the score never goes below 0.
 */
@Component
public class FormattingAnalyzer {

    // Thresholds (documented)
    private static final int    MIN_CONTENT_CHARS       = 100;    // Below this → severe deduction
    private static final double MAX_WHITESPACE_RATIO    = 0.40;   // >40% whitespace lines → deduction
    private static final int    MAX_REPEATED_CHAR_RUN   = 10;     // Run of same char longer than this
    private static final int    MAX_LINE_LENGTH         = 300;    // Single line longer than this → flag
    private static final double MAX_GARBLED_CHAR_RATIO  = 0.03;   // >3% non-printable → deduction

    /**
     * Analyze text formatting quality and return a score 0–100.
     */
    public int score(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }

        int score = 100;

        // Check 1: Content length
        if (text.length() < MIN_CONTENT_CHARS) {
            score -= 40;
        } else if (text.length() < 300) {
            score -= 20;
        }

        // Check 2: Whitespace ratio
        String[] lines = text.split("\n");
        long blankLines = java.util.Arrays.stream(lines)
                .filter(l -> l.isBlank()).count();
        double whitespaceRatio = (double) blankLines / Math.max(1, lines.length);
        if (whitespaceRatio > MAX_WHITESPACE_RATIO) {
            score -= (int) Math.round((whitespaceRatio - MAX_WHITESPACE_RATIO) * 100);
        }

        // Check 3: Repeated characters (decorative separators like ===== or ----)
        Pattern repeatedChars = Pattern.compile("(.)\\1{" + MAX_REPEATED_CHAR_RUN + ",}");
        if (repeatedChars.matcher(text).find()) {
            score -= 10;
        }

        // Check 4: Extremely long lines (possible parse artifacts — no line breaks)
        long longLines = java.util.Arrays.stream(lines)
                .filter(l -> l.length() > MAX_LINE_LENGTH).count();
        if (longLines > 3) {
            score -= 15;
        } else if (longLines > 0) {
            score -= 5;
        }

        // Check 5: Garbled / non-printable characters
        long nonPrintable = text.chars()
                .filter(c -> c < 32 && c != '\n' && c != '\r' && c != '\t')
                .count();
        double garbledRatio = (double) nonPrintable / text.length();
        if (garbledRatio > MAX_GARBLED_CHAR_RATIO) {
            score -= 20;
        } else if (garbledRatio > 0.01) {
            score -= 10;
        }

        return Math.min(100, Math.max(0, score));
    }
}
