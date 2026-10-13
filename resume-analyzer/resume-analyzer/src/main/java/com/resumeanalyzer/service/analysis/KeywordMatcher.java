package com.resumeanalyzer.service.analysis;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Keyword Matcher — deterministic, stopword-filtered, token-boundary aware.
 *
 * Algorithm:
 * 1. Tokenize JD text into meaningful tokens (strip punctuation, lowercase).
 * 2. Remove common English stopwords.
 * 3. For each meaningful JD token, count occurrences in resume text using
 *    word-boundary matching (so "Java" doesn't match inside "JavaScript").
 * 4. Compute matched/missing lists and a keyword match score.
 *
 * NOTE: This is a project-defined keyword analysis approach.
 * It does NOT represent the exact methodology of any commercial ATS system.
 */
@Component
public class KeywordMatcher {

    // Common English stopwords to exclude from keyword matching
    private static final Set<String> STOPWORDS = Set.of(
            "the", "and", "or", "a", "an", "to", "of", "for", "with", "in", "on",
            "is", "are", "be", "as", "from", "that", "this", "at", "by", "we",
            "you", "our", "your", "their", "they", "will", "have", "has",
            "had", "not", "do", "does", "did", "but", "if", "it", "its", "all",
            "any", "been", "can", "could", "should", "would", "may", "might",
            "must", "shall", "who", "what", "which", "when", "where", "how",
            "than", "then", "so", "such", "about", "into", "through", "during",
            "before", "after", "above", "below", "between", "each", "more",
            "most", "other", "some", "also", "both", "up", "out", "use", "used",
            "using", "new", "work", "working", "role", "experience", "team",
            "strong", "good", "well", "able", "us", "join", "help", "including",
            "plus", "bonus", "provide", "support", "ensure", "based", "need",
            "understand", "knowledge", "ability", "skills", "skill"
    );

    // Minimum keyword length to consider (avoids matching single-letter tokens)
    private static final int MIN_KEYWORD_LENGTH = 3;

    /**
     * Result record for keyword matching.
     */
    public record KeywordMatchResult(
            List<String> matchedKeywords,
            List<String> missingKeywords,
            Map<String, Integer> keywordFrequenciesInResume,
            Map<String, Integer> keywordFrequenciesInJd,
            int keywordMatchScore
    ) {}

    /**
     * Extract meaningful keywords from a text (JD or resume).
     */
    public List<String> extractKeywords(String text) {
        if (text == null || text.isBlank()) return List.of();

        return Arrays.stream(tokenize(text))
                .map(KeywordMatcher::stripSentencePeriod)
                .filter(token -> !STOPWORDS.contains(token))
                .filter(token -> token.length() >= MIN_KEYWORD_LENGTH)
                .filter(token -> token.matches("[a-z][a-z0-9+#./_-]*")) // must start with a letter
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * Match JD keywords against resume text, computing frequencies and score.
     */
    public KeywordMatchResult match(String resumeText, String jdText) {
        if (resumeText == null) resumeText = "";
        if (jdText == null) jdText = "";

        List<String> jdKeywords = extractKeywords(jdText);

        List<String> matched   = new ArrayList<>();
        List<String> missing   = new ArrayList<>();
        Map<String, Integer> resumeFreqs = new LinkedHashMap<>();
        Map<String, Integer> jdFreqs     = new LinkedHashMap<>();

        for (String keyword : jdKeywords) {
            int resumeCount = countOccurrences(resumeText.toLowerCase(), keyword);
            int jdCount     = countOccurrences(jdText.toLowerCase(), keyword);
            resumeFreqs.put(keyword, resumeCount);
            jdFreqs.put(keyword, jdCount);
            if (resumeCount > 0) {
                matched.add(keyword);
            } else {
                missing.add(keyword);
            }
        }

        int score = computeScore(matched.size(), missing.size());

        return new KeywordMatchResult(
                Collections.unmodifiableList(matched),
                Collections.unmodifiableList(missing),
                Collections.unmodifiableMap(resumeFreqs),
                Collections.unmodifiableMap(jdFreqs),
                score
        );
    }

    /**
     * Count whole-word occurrences of a keyword in text (already lowercased).
     * Uses word-boundary (\b) matching to avoid partial matches.
     * e.g. "java" does NOT match inside "javascript".
     */
    public int countOccurrences(String lowerText, String keyword) {
        if (lowerText == null || keyword == null || keyword.isBlank()) return 0;
        String escaped = Pattern.quote(keyword);
        Pattern p = Pattern.compile("\\b" + escaped + "\\b", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(lowerText);
        int count = 0;
        while (m.find()) count++;
        return count;
    }

    /**
     * Remove a trailing sentence period from a token.
     *
     * Tokenization keeps "." inside a token so that genuine names such as
     * "node.js" survive, but that also lets a sentence-final period through:
     * "experience." and "required." would otherwise be treated as distinct
     * keywords and count as permanently "missing", depressing the keyword score
     * and producing nonsense items in the recommendations. Only trailing periods
     * are removed — "+", "#", "_", "-" and "/" are preserved so tokens like
     * "c++", "c#" and "ci/cd" are unchanged.
     */
    private static String stripSentencePeriod(String token) {
        if (!token.endsWith(".")) {
            return token;
        }
        return token.replaceAll("\\.+$", "");
    }

    /** Tokenize text into lowercase tokens, stripping common punctuation. */
    private String[] tokenize(String text) {
        return text.toLowerCase()
                .replaceAll("[^a-z0-9+#./_ \\-]", " ")
                .split("\\s+");
    }

    private int computeScore(int matched, int missing) {
        int total = matched + missing;
        if (total == 0) return 50; // No keywords found — neutral
        return (int) Math.round(((double) matched / total) * 100.0);
    }
}
