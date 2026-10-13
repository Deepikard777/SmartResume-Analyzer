package com.resumeanalyzer.service.analysis;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates actionable recommendations from the *deterministic* findings of an
 * analysis run. It performs no AI / LLM inference and there is no language model
 * anywhere in this project.
 *
 * Hard rule: this class never invents resume content, qualifications or skills.
 * Each recommendation restates a measured fact (what the job description asked for
 * and what the resume text did or did not contain) and then describes the action.
 * Where a skill is missing, the wording tells the user to add evidence *only if they
 * genuinely have it* — it never suggests claiming something they do not have.
 */
@Component
public class RecommendationGenerator {

    public static final String PRIORITY_HIGH = "HIGH";
    public static final String PRIORITY_MEDIUM = "MEDIUM";
    public static final String PRIORITY_LOW = "LOW";

    /** Keep the payload readable — the UI shows at most a handful of cards. */
    private static final int MAX_ITEMS_PER_RECOMMENDATION = 12;
    private static final int MAX_RECOMMENDATIONS = 8;

    /** Below this relevance score the JD's emphasis is not being covered well. */
    private static final int LOW_RELEVANCE_THRESHOLD = 60;

    /** Below this formatting score the extracted text itself is worth fixing. */
    private static final int LOW_FORMATTING_THRESHOLD = 80;

    /**
     * One actionable item.
     *
     * @param category    Skills / Keywords / Structure / Repetition / Formatting
     * @param priority    HIGH, MEDIUM or LOW
     * @param title       short imperative summary
     * @param detail      why it matters, quoting only measured values
     * @param relatedItems the concrete skills / keywords / sections it refers to
     */
    public record Recommendation(
            String category,
            String priority,
            String title,
            String detail,
            List<String> relatedItems
    ) {}

    /** Everything the generator is allowed to look at. */
    public record Input(
            List<String> missingSkills,
            List<String> optionalSkills,
            List<String> missingKeywords,
            Map<String, Integer> jdKeywordFrequencies,
            List<ResumeStructureAnalyzer.SectionCheck> structureChecks,
            String stuffingRisk,
            List<KeywordStuffingDetector.FlaggedEntry> flaggedKeywords,
            int skillMatchScore,
            int keywordMatchScore,
            int relevanceScore,
            int structureScore,
            int formattingScore
    ) {}

    public List<Recommendation> generate(Input input) {
        List<Recommendation> recommendations = new ArrayList<>();

        addMissingSkills(recommendations, input);
        addMissingKeywords(recommendations, input);
        addStructureWarnings(recommendations, input);
        addRepetitionRisk(recommendations, input);
        addRelevanceGap(recommendations, input);
        addFormattingFix(recommendations, input);

        // Highest priority first, then a stable category order for equal priorities.
        recommendations.sort(Comparator
                .comparingInt((Recommendation r) -> priorityRank(r.priority()))
                .thenComparing(Recommendation::category));

        if (recommendations.size() > MAX_RECOMMENDATIONS) {
            return List.copyOf(recommendations.subList(0, MAX_RECOMMENDATIONS));
        }
        return List.copyOf(recommendations);
    }

    // -----------------------------------------------------------------------
    // Individual rules
    // -----------------------------------------------------------------------

    private void addMissingSkills(List<Recommendation> out, Input input) {
        List<String> missing = safe(input.missingSkills());
        if (missing.isEmpty()) {
            return;
        }
        List<String> shown = cap(missing);
        out.add(new Recommendation(
                "Skills",
                PRIORITY_HIGH,
                "Address the required skills the job description asks for and the resume does not mention",
                "The job description requires " + joinList(shown) + ", and none of these were found in the "
                        + "resume text (" + missing.size() + " skill" + (missing.size() == 1 ? "" : "s")
                        + " missing). For each one you genuinely have, add a concrete role or project bullet "
                        + "that names the skill and what you did with it. If you have not used it, treat it as "
                        + "a real gap — listing a skill you cannot evidence will not survive a technical interview.",
                shown));
    }

    private void addMissingKeywords(List<Recommendation> out, Input input) {
        List<String> missing = safe(input.missingKeywords());
        if (missing.isEmpty()) {
            return;
        }

        // Order by how much the JD itself emphasises each missing keyword, so the
        // advice targets the language the employer actually repeats.
        Map<String, Integer> jdFrequencies = input.jdKeywordFrequencies() == null
                ? Map.of() : input.jdKeywordFrequencies();

        List<String> ordered;
        try {
            ordered = missing.stream()
                    .sorted(Comparator.comparingInt(
                            (String keyword) -> jdFrequencies.getOrDefault(keyword, 1)).reversed())
                    .toList();
        } catch (UnsupportedOperationException e) {
            ordered = List.copyOf(missing);
        }

        List<String> shown = cap(ordered);
        out.add(new Recommendation(
                "Keywords",
                PRIORITY_HIGH,
                "Mirror the job description's own wording where it is accurate",
                "These terms appear in the job description but not in the resume: " + joinList(shown)
                        + " (" + missing.size() + " missing of the terms extracted from the job description). "
                        + "Where a term genuinely describes work you have done, use the employer's wording "
                        + "rather than a synonym. Each term is listed with the number of times the job "
                        + "description itself uses it: " + withEmphasis(shown, jdFrequencies) + ".",
                shown));
    }

    private void addStructureWarnings(List<Recommendation> out, Input input) {
        List<ResumeStructureAnalyzer.SectionCheck> checks = input.structureChecks() == null
                ? List.of() : input.structureChecks();

        List<String> warned = checks.stream()
                .filter(check -> "WARNING".equals(check.status()))
                .map(ResumeStructureAnalyzer.SectionCheck::section)
                .toList();

        if (warned.isEmpty()) {
            return;
        }
        out.add(new Recommendation(
                "Structure",
                PRIORITY_MEDIUM,
                "Add the resume sections an ATS parser expects to find",
                "The structure scan found " + warned.size() + " section"
                        + (warned.size() == 1 ? "" : "s") + " that could not be detected: "
                        + joinList(cap(warned)) + ". Use a plain, conventional heading for each one so both "
                        + "a parser and a human reviewer can locate it. Optional sections being absent is a "
                        + "warning, not a failure — add them only if you have real content for them. "
                        + "Overall structure score: " + input.structureScore() + "/100.",
                cap(warned)));
    }

    private void addRepetitionRisk(List<Recommendation> out, Input input) {
        String risk = input.stuffingRisk();
        if (risk == null || "LOW".equalsIgnoreCase(risk)) {
            return;
        }
        List<KeywordStuffingDetector.FlaggedEntry> flagged = input.flaggedKeywords() == null
                ? List.of() : input.flaggedKeywords();
        if (flagged.isEmpty()) {
            return;
        }

        List<String> items = flagged.stream()
                .map(entry -> entry.keyword() + " (" + entry.frequency() + "x)")
                .limit(MAX_ITEMS_PER_RECOMMENDATION)
                .toList();

        out.add(new Recommendation(
                "Repetition",
                "HIGH".equalsIgnoreCase(risk) ? PRIORITY_HIGH : PRIORITY_MEDIUM,
                "Reduce repeated keyword instances flagged by the stuffing check",
                "The repetition check flagged " + joinList(items) + " at " + risk.toUpperCase()
                        + " risk. Repetition is measured against the project's own density thresholds and "
                        + "produces the per-keyword figures shown in the analysis. Legitimate technical "
                        + "resumes do repeat core terms, so treat this as a readability signal: consolidate "
                        + "repeated mentions into a single strong statement rather than deleting the term.",
                items));
    }

    private void addRelevanceGap(List<Recommendation> out, Input input) {
        if (input.relevanceScore() >= LOW_RELEVANCE_THRESHOLD) {
            return;
        }
        out.add(new Recommendation(
                "Keywords",
                PRIORITY_MEDIUM,
                "Prioritise the terms the job description emphasises most",
                "Relevance is " + input.relevanceScore() + "/100. Relevance weights each job-description term "
                        + "by how often the description itself uses it, so it is lower than the plain keyword "
                        + "match of " + input.keywordMatchScore() + " when the employer's most-repeated terms "
                        + "are the ones missing. Work through the terms listed under the keyword recommendation "
                        + "in the order given, starting with the most repeated.",
                List.of()));
    }

    private void addFormattingFix(List<Recommendation> out, Input input) {
        if (input.formattingScore() >= LOW_FORMATTING_THRESHOLD) {
            return;
        }
        out.add(new Recommendation(
                "Formatting",
                PRIORITY_LOW,
                "Improve the text quality of the document so parsing is reliable",
                "The formatting check scored " + input.formattingScore() + "/100 on the extracted text. "
                        + "This score reflects parsing quality — very short content, an excess of blank lines, "
                        + "decorative separator runs, extremely long lines. It is measured on extracted text "
                        + "only and cannot see fonts, margins or colours. Exporting to a text-based PDF or a "
                        + "DOCX usually resolves this.",
                List.of()));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static List<String> safe(List<String> values) {
        return values == null ? List.of() : values;
    }

    private static List<String> cap(List<String> values) {
        return values.size() > MAX_ITEMS_PER_RECOMMENDATION
                ? List.copyOf(values.subList(0, MAX_ITEMS_PER_RECOMMENDATION))
                : List.copyOf(values);
    }

    private static String joinList(List<String> values) {
        if (values.isEmpty()) {
            return "—";
        }
        if (values.size() == 1) {
            return values.get(0);
        }
        return String.join(", ", values.subList(0, values.size() - 1))
                + " and " + values.get(values.size() - 1);
    }

    private static String withEmphasis(List<String> keywords, Map<String, Integer> jdFrequencies) {
        Map<String, Integer> ordered = new LinkedHashMap<>();
        for (String keyword : keywords) {
            ordered.put(keyword, jdFrequencies.getOrDefault(keyword, 0));
        }
        StringBuilder builder = new StringBuilder();
        ordered.forEach((keyword, count) -> {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(keyword).append(" x").append(count);
        });
        return builder.toString();
    }

    private static int priorityRank(String priority) {
        if (PRIORITY_HIGH.equals(priority)) {
            return 0;
        }
        if (PRIORITY_MEDIUM.equals(priority)) {
            return 1;
        }
        return 2;
    }
}
