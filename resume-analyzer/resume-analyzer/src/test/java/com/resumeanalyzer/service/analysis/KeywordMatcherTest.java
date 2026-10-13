package com.resumeanalyzer.service.analysis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Focused unit tests for keyword extraction.
 *
 * These pin down the sentence-period fix: a token must not be treated as a keyword
 * just because a period was attached to it, while genuine dotted or symbol-suffixed
 * names must survive.
 */
class KeywordMatcherTest {

    private final KeywordMatcher matcher = new KeywordMatcher();

    @Test
    @DisplayName("A sentence-final period does not create a phantom keyword")
    void sentencePeriodDoesNotCreatePhantomKeyword() {
        List<String> keywords = matcher.extractKeywords(
                "We are looking for a software engineer with Java experience. "
                        + "Strong communication is required.");

        assertThat(keywords).doesNotContain("experience.");
        assertThat(keywords).doesNotContain("required.");
        // "experience" alone is a stopword, so it must not reappear as a keyword either.
        assertThat(keywords).doesNotContain("experience");
        // "required." normalises to a real word and is kept.
        assertThat(keywords).contains("required");
    }

    @Test
    @DisplayName("Dotted and symbol-suffixed names are preserved")
    void dottedAndSymbolSuffixedNamesSurvive() {
        List<String> keywords = matcher.extractKeywords(
                "Experience with Node.js, ASP.NET, C++, C# and CI/CD pipelines.");

        assertThat(keywords).contains("node.js");
        assertThat(keywords).contains("asp.net");
        assertThat(keywords).contains("c++");
        assertThat(keywords).contains("ci/cd");

        // Documented limitation, not a regression: MIN_KEYWORD_LENGTH is 3, so a
        // two-character token such as "c#" is not treated as a keyword.
        assertThat(keywords).doesNotContain("c#");
    }

    @Test
    @DisplayName("Stopwords and very short tokens are excluded")
    void stopwordsAndShortTokensAreExcluded() {
        List<String> keywords = matcher.extractKeywords(
                "The team uses the tools and the best practices to do the work.");

        assertThat(keywords).doesNotContain("the");
        assertThat(keywords).doesNotContain("and");
        assertThat(keywords).doesNotContain("to");
        // "uses" is an inflected verb that is NOT in the stopword list, so it is
        // retained. That is existing behaviour and is asserted here so the list
        // cannot drift unnoticed.
        assertThat(keywords).contains("uses");
    }

    @Test
    @DisplayName("Whole-word matching does not match a keyword inside a longer word")
    void wholeWordMatchingAvoidsPartialMatches() {
        // "java" must not match inside "javascript".
        assertThat(matcher.countOccurrences("i write javascript daily", "java")).isZero();
        assertThat(matcher.countOccurrences("i write java daily", "java")).isEqualTo(1);
    }
}
