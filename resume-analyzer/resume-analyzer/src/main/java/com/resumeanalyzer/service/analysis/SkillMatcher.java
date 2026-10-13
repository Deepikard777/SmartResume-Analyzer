package com.resumeanalyzer.service.analysis;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Skill Matcher — deterministic, dictionary-based.
 *
 * Algorithm:
 * 1. For each canonical skill in the dictionary, check all its aliases against
 *    the target text using whole-word regex (word-boundary aware).
 * 2. Multi-word skills (e.g. "Spring Boot") are matched as a phrase.
 * 3. Single-character skills (C, R) are matched with extra care to avoid
 *    false positives from single-letter words in sentences.
 * 4. "Optional" skills are detected by checking whether the JD mentions the
 *    skill near an optional-signal phrase ("nice to have", "preferred", etc.)
 *    within a ±150 character window.
 *
 * Matching is case-insensitive. Results use display names (original case).
 */
@Component
public class SkillMatcher {

    private static final Set<String> OPTIONAL_SIGNAL_PHRASES = Set.of(
            "nice to have", "nice-to-have", "preferred", "bonus", "optional",
            "desirable", "good to have", "advantageous", "plus", "ideally"
    );

    // Window (chars) around a skill mention to look for optional signals
    private static final int OPTIONAL_WINDOW = 200;

    private final SkillDictionary dictionary;

    public SkillMatcher(SkillDictionary dictionary) {
        this.dictionary = dictionary;
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Find all skills present in the given text.
     *
     * @return list of display-name skills found
     */
    public List<String> extractSkills(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String lower = text.toLowerCase();
        List<String> found = new ArrayList<>();
        for (String canonical : dictionary.getAllCanonicals()) {
            if (containsSkill(lower, canonical)) {
                found.add(dictionary.getDisplayName(canonical));
            }
        }
        return Collections.unmodifiableList(found);
    }

    /**
     * Result record for a full skill comparison.
     */
    public record SkillMatchResult(
            List<String> matchedSkills,
            List<String> missingSkills,
            List<String> optionalSkills,
            int skillMatchScore
    ) {}

    /**
     * Compare resume skills against JD skills.
     *
     * @param resumeText full extracted resume text
     * @param jdText     full job description text
     * @return SkillMatchResult with matched/missing/optional lists and score
     */
    public SkillMatchResult match(String resumeText, String jdText) {
        if (resumeText == null) resumeText = "";
        if (jdText == null) jdText = "";

        String resumeLower = resumeText.toLowerCase();
        String jdLower     = jdText.toLowerCase();

        Set<String> jdCanonicals = new LinkedHashSet<>();
        for (String canonical : dictionary.getAllCanonicals()) {
            if (containsSkill(jdLower, canonical)) {
                jdCanonicals.add(canonical);
            }
        }

        List<String> matched  = new ArrayList<>();
        List<String> missing  = new ArrayList<>();
        List<String> optional = new ArrayList<>();

        for (String canonical : jdCanonicals) {
            String displayName = dictionary.getDisplayName(canonical);
            boolean isOptional = isSkillOptional(jdLower, canonical);
            boolean inResume   = containsSkill(resumeLower, canonical);

            if (inResume) {
                matched.add(displayName);
            } else if (isOptional) {
                optional.add(displayName);
            } else {
                missing.add(displayName);
            }
        }

        int score = computeScore(matched.size(), missing.size(), optional.size());

        return new SkillMatchResult(
                Collections.unmodifiableList(matched),
                Collections.unmodifiableList(missing),
                Collections.unmodifiableList(optional),
                score
        );
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    /**
     * Returns true if any alias of the canonical skill is found in the text
     * using whole-word matching.
     */
    boolean containsSkill(String lowerText, String canonical) {
        for (String alias : dictionary.getAliasesFor(canonical)) {
            if (matchesWholeWord(lowerText, alias)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whole-word / phrase match for an alias in lowercased text.
     *
     * Multi-word aliases (e.g. "spring boot") are matched as a phrase.
     * Very short single-letter aliases ("c", "r") use stricter boundaries.
     */
    private boolean matchesWholeWord(String lowerText, String alias) {
        if (alias.isBlank()) return false;

        // Escape special regex chars in alias
        String escaped = Pattern.quote(alias);

        // For special token like "c++" or "c#" or "asp.net" include literal punctuation
        String pattern = "(?<![\\w+#.])" + escaped + "(?![\\w+#.])";

        // Very short tokens (1 char) — require word boundary on both sides strictly
        if (alias.length() == 1) {
            pattern = "\\b" + escaped + "\\b";
        }

        return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(lowerText).find();
    }

    /**
     * Checks whether the skill appears near an "optional" signal phrase in the JD.
     * Scans a ±OPTIONAL_WINDOW character window around each occurrence of the skill.
     */
    private boolean isSkillOptional(String jdLower, String canonical) {
        for (String alias : dictionary.getAliasesFor(canonical)) {
            int idx = jdLower.indexOf(alias);
            while (idx >= 0) {
                int start = Math.max(0, idx - OPTIONAL_WINDOW);
                int end   = Math.min(jdLower.length(), idx + alias.length() + OPTIONAL_WINDOW);
                String window = jdLower.substring(start, end);
                for (String signal : OPTIONAL_SIGNAL_PHRASES) {
                    if (window.contains(signal)) {
                        return true;
                    }
                }
                idx = jdLower.indexOf(alias, idx + 1);
            }
        }
        return false;
    }

    /**
     * Computes skill match score (0–100).
     * Optional missing skills are penalized at 50% of full missing skill weight.
     *
     * Formula:
     *   effective_total = required_total + 0.5 * optional_count
     *   score = matched / effective_total * 100
     *
     * If JD has no recognized skills → score = 50 (neutral, no data).
     */
    private int computeScore(int matched, int missing, int optional) {
        double requiredTotal  = matched + missing;
        double effectiveTotal = requiredTotal + (optional * 0.5);

        if (effectiveTotal == 0) {
            return 50; // No recognized JD skills — neutral
        }

        double score = ((matched + (optional * 0.5 * 0)) / effectiveTotal) * 100.0;
        // Simplify: matched / (matched + missing + 0.5*optional) * 100
        score = ((double) matched / effectiveTotal) * 100.0;
        return (int) Math.round(Math.min(100, Math.max(0, score)));
    }
}
