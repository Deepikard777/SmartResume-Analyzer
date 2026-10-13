package com.resumeanalyzer.service.analysis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the deterministic recommendation rule set.
 *
 * These assert that recommendations are produced only from real findings, that
 * they are ordered HIGH → MEDIUM → LOW, and that the generator never invents a
 * skill or claim that is not in the input.
 */
class RecommendationGeneratorTest {

    private final RecommendationGenerator generator = new RecommendationGenerator();

    private static final List<ResumeStructureAnalyzer.SectionCheck> ALL_PASSED = List.of(
            new ResumeStructureAnalyzer.SectionCheck("Contact Information", "PASSED", "Contact Information section detected."),
            new ResumeStructureAnalyzer.SectionCheck("Technical Skills", "PASSED", "Technical Skills section detected."),
            new ResumeStructureAnalyzer.SectionCheck("Work Experience", "PASSED", "Work Experience section detected."),
            new ResumeStructureAnalyzer.SectionCheck("Education", "PASSED", "Education section detected."));

    private RecommendationGenerator.Input input(
            List<String> missingSkills,
            List<String> missingKeywords,
            Map<String, Integer> jdFrequencies,
            List<ResumeStructureAnalyzer.SectionCheck> structure,
            String risk,
            List<KeywordStuffingDetector.FlaggedEntry> flagged,
            int skillMatch,
            int keywordMatch,
            int relevance,
            int structureScore,
            int formatting) {
        return new RecommendationGenerator.Input(
                missingSkills, List.of(), missingKeywords, jdFrequencies, structure,
                risk, flagged, skillMatch, keywordMatch, relevance, structureScore, formatting);
    }

    @Test
    @DisplayName("Missing required skills produce a HIGH priority Skills recommendation naming them")
    void missingSkillsProduceHighPriorityRecommendation() {
        var result = generator.generate(input(
                List.of("Docker", "AWS"), List.of(), Map.of(),
                ALL_PASSED, "LOW", List.of(), 75, 80, 90, 100, 100));

        assertThat(result).hasSize(1);
        var recommendation = result.get(0);
        assertThat(recommendation.category()).isEqualTo("Skills");
        assertThat(recommendation.priority()).isEqualTo(RecommendationGenerator.PRIORITY_HIGH);
        assertThat(recommendation.relatedItems()).containsExactly("Docker", "AWS");
        assertThat(recommendation.detail()).contains("Docker").contains("AWS").contains("2 skills");
        // The advice must tell the user not to claim what they cannot evidence.
        assertThat(recommendation.detail()).contains("If you have not used it");
    }

    @Test
    @DisplayName("A clean analysis produces no recommendations")
    void cleanAnalysisProducesNothing() {
        var result = generator.generate(input(
                List.of(), List.of(), Map.of(), ALL_PASSED, "LOW", List.of(),
                100, 100, 100, 100, 100));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Missing keywords are ordered by how often the job description repeats them")
    void missingKeywordsAreOrderedByJobDescriptionEmphasis() {
        var result = generator.generate(input(
                List.of(), List.of("microservices", "docker", "aws"),
                Map.of("microservices", 5, "docker", 1, "aws", 3),
                ALL_PASSED, "LOW", List.of(), 50, 40, 80, 100, 100));

        assertThat(result).hasSize(1);
        var recommendation = result.get(0);
        assertThat(recommendation.category()).isEqualTo("Keywords");
        // microservices (x5) must come before aws (x3) and docker (x1).
        assertThat(recommendation.relatedItems())
                .containsExactly("microservices", "aws", "docker");
        assertThat(recommendation.detail()).contains("microservices x5");
    }

    @Test
    @DisplayName("Keyword-stuffing flags produce a Repetition recommendation at the matching priority")
    void stuffingFlagsProduceRepetitionRecommendation() {
        var flagged = List.of(
                new KeywordStuffingDetector.FlaggedEntry("java", 12, "Appears 12 times."),
                new KeywordStuffingDetector.FlaggedEntry("spring", 6, "Appears 6 times."));

        var result = generator.generate(input(
                List.of(), List.of(), Map.of(), ALL_PASSED, "HIGH", flagged,
                80, 80, 80, 100, 100));

        assertThat(result).hasSize(1);
        var recommendation = result.get(0);
        assertThat(recommendation.category()).isEqualTo("Repetition");
        assertThat(recommendation.priority()).isEqualTo(RecommendationGenerator.PRIORITY_HIGH);
        assertThat(recommendation.relatedItems()).containsExactly("java (12x)", "spring (6x)");
    }

    @Test
    @DisplayName("Structure warnings produce a MEDIUM priority Structure recommendation")
    void structureWarningsProduceRecommendation() {
        var structure = List.of(
                new ResumeStructureAnalyzer.SectionCheck("Contact Information", "PASSED", "detected"),
                new ResumeStructureAnalyzer.SectionCheck("Internships", "WARNING",
                        "No internships section detected. (optional section)"));

        var result = generator.generate(input(
                List.of(), List.of(), Map.of(), structure, "LOW", List.of(),
                80, 80, 80, 95, 100));

        assertThat(result).hasSize(1);
        var recommendation = result.get(0);
        assertThat(recommendation.category()).isEqualTo("Structure");
        assertThat(recommendation.priority()).isEqualTo(RecommendationGenerator.PRIORITY_MEDIUM);
        assertThat(recommendation.relatedItems()).containsExactly("Internships");
    }

    @Test
    @DisplayName("Low relevance and low formatting add their own recommendations")
    void lowScoresProduceTheirOwnRecommendations() {
        var result = generator.generate(input(
                List.of(), List.of(), Map.of(), ALL_PASSED, "LOW", List.of(),
                80, 55, 40, 100, 60));

        assertThat(result).extracting(RecommendationGenerator.Recommendation::category)
                .containsExactlyInAnyOrder("Keywords", "Formatting");
    }

    @Test
    @DisplayName("Recommendations are ordered HIGH before MEDIUM before LOW")
    void recommendationsAreOrderedByPriority() {
        var structure = List.of(new ResumeStructureAnalyzer.SectionCheck(
                "Internships", "WARNING", "No internships section detected. (optional section)"));
        var flagged = List.of(new KeywordStuffingDetector.FlaggedEntry("java", 12, "Appears 12 times."));

        var result = generator.generate(input(
                List.of("Docker"), List.of(), Map.of(), structure, "HIGH", flagged,
                80, 80, 80, 90, 50));

        assertThat(result).hasSize(4);
        assertThat(result).extracting(RecommendationGenerator.Recommendation::priority)
                .containsExactly(
                        RecommendationGenerator.PRIORITY_HIGH,
                        RecommendationGenerator.PRIORITY_HIGH,
                        RecommendationGenerator.PRIORITY_MEDIUM,
                        RecommendationGenerator.PRIORITY_LOW);
    }
}
