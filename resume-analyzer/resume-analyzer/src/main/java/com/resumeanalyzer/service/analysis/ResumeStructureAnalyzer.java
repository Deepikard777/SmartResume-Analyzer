package com.resumeanalyzer.service.analysis;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Resume Structure Analyzer — deterministic section detection.
 *
 * Uses regex patterns to detect common resume section headings.
 * Patterns are matched case-insensitively at line boundaries.
 *
 * Sections checked:
 *   Contact Information, Summary/Profile, Skills, Experience, Education,
 *   Projects, Certifications, Achievements, Internships.
 *
 * NOTE: This is a heuristic approach based on common resume heading patterns.
 * Resume formats vary widely; this analyzer will not catch every format.
 * Missing sections produce a WARNING, not a hard failure.
 */
@Component
public class ResumeStructureAnalyzer {

    /**
     * A detected or missing section with its status.
     */
    public record SectionCheck(String section, String status, String message) {}

    /**
     * Result of structure analysis.
     */
    public record StructureResult(
            List<SectionCheck> checks,
            int structureScore
    ) {}

    /**
     * Defines each section: display name, regex patterns, whether it's required.
     */
    private record SectionDefinition(String displayName, List<Pattern> patterns, boolean required) {}

    private final List<SectionDefinition> sections;

    public ResumeStructureAnalyzer() {
        sections = List.of(
            section("Contact Information", true,
                    "\\b(email|phone|mobile|linkedin|github|address|contact)\\b",
                    "\\b[\\w._%+-]+@[\\w.-]+\\.[a-z]{2,}\\b",  // email pattern
                    "\\b(\\+?\\d[\\d\\s\\-]{8,14}\\d)\\b"       // phone pattern
            ),
            section("Professional Summary", false,
                    "^\\s*(professional\\s+)?summary\\s*$",
                    "^\\s*profile\\s*$",
                    "^\\s*objective\\s*$",
                    "^\\s*about\\s+me\\s*$",
                    "^\\s*career\\s+objective\\s*$",
                    "^\\s*professional\\s+profile\\s*$"
            ),
            section("Technical Skills", true,
                    "^\\s*(technical\\s+)?skills?\\s*$",
                    "^\\s*core\\s+(competencies|skills)\\s*$",
                    "^\\s*key\\s+skills\\s*$",
                    "^\\s*technologies\\s*$",
                    "^\\s*tech(nical)?\\s+stack\\s*$",
                    "^\\s*areas\\s+of\\s+expertise\\s*$"
            ),
            section("Work Experience", true,
                    "^\\s*(work\\s+)?experience\\s*$",
                    "^\\s*professional\\s+experience\\s*$",
                    "^\\s*employment\\s+(history)?\\s*$",
                    "^\\s*work\\s+history\\s*$",
                    "^\\s*career\\s+history\\s*$",
                    "^\\s*job\\s+experience\\s*$"
            ),
            section("Education", true,
                    "^\\s*education\\s*$",
                    "^\\s*educational\\s+(background|qualifications?)\\s*$",
                    "^\\s*academic\\s+(background|qualifications?)\\s*$",
                    "^\\s*academics?\\.?\\s*$",
                    "\\b(b\\.?tech|b\\.?e|b\\.?sc|m\\.?tech|m\\.?sc|mba|bachelor|master|ph\\.?d|degree)\\b"
            ),
            section("Projects", false,
                    "^\\s*projects?\\.?\\s*$",
                    "^\\s*(personal|academic|key)\\s+projects?\\.?\\s*$",
                    "^\\s*notable\\s+projects?\\.?\\s*$"
            ),
            section("Certifications", false,
                    "^\\s*certifications?\\.?\\s*$",
                    "^\\s*(professional\\s+)?certificates?\\.?\\s*$",
                    "^\\s*licenses?\\s+(&|and)\\s+certifications?\\.?\\s*$",
                    "\\b(certified|certification|aws certified|google certified|microsoft certified)\\b"
            ),
            section("Internships", false,
                    "^\\s*internships?\\.?\\s*$",
                    "^\\s*internship\\s+experience\\.?\\s*$",
                    "^\\s*industrial\\s+training\\.?\\s*$"
            )
        );
    }

    /**
     * Analyze the structure of a resume text.
     *
     * @param resumeText the full extracted resume text
     * @return StructureResult with per-section checks and overall score
     */
    public StructureResult analyze(String resumeText) {
        if (resumeText == null || resumeText.isBlank()) {
            List<SectionCheck> allMissing = new ArrayList<>();
            for (SectionDefinition def : sections) {
                allMissing.add(new SectionCheck(def.displayName(), "WARNING",
                        "No resume text available."));
            }
            return new StructureResult(Collections.unmodifiableList(allMissing), 0);
        }

        String lowerText = resumeText.toLowerCase();
        List<SectionCheck> checks = new ArrayList<>();
        int passed = 0;
        int required = 0;

        for (SectionDefinition def : sections) {
            if (def.required()) required++;

            boolean found = matchesAnyPattern(lowerText, def.patterns());
            String status;
            String message;

            if (found) {
                status = "PASSED";
                message = def.displayName() + " section detected.";
                passed++;
            } else {
                status = "WARNING";
                message = "No " + def.displayName().toLowerCase() + " section detected.";
                if (!def.required()) {
                    message += " (optional section)";
                }
            }
            checks.add(new SectionCheck(def.displayName(), status, message));
        }

        int score = computeScore(checks);
        return new StructureResult(Collections.unmodifiableList(checks), score);
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private boolean matchesAnyPattern(String lowerText, List<Pattern> patterns) {
        for (Pattern p : patterns) {
            if (p.matcher(lowerText).find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Structure score formula:
     *   - Required sections: each worth 20 points (4 required = 80 pts max)
     *   - Optional sections: each worth 5 points (4 optional = 20 pts max)
     *   Total max = 100
     */
    private int computeScore(List<SectionCheck> checks) {
        int score = 0;
        for (int i = 0; i < sections.size() && i < checks.size(); i++) {
            if ("PASSED".equals(checks.get(i).status())) {
                score += sections.get(i).required() ? 20 : 5;
            }
        }
        return Math.min(100, score);
    }

    private SectionDefinition section(String name, boolean required, String... regexes) {
        List<Pattern> patterns = new ArrayList<>();
        for (String r : regexes) {
            patterns.add(Pattern.compile(r, Pattern.CASE_INSENSITIVE | Pattern.MULTILINE));
        }
        return new SectionDefinition(name, Collections.unmodifiableList(patterns), required);
    }
}
